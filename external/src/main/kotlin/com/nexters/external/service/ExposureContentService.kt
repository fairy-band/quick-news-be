package com.nexters.external.service

import com.nexters.external.annotation.ExposureContentChanged
import com.nexters.external.entity.Content
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.Summary
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExploreContentRow
import com.nexters.external.repository.ExposureContentArchiveRow
import com.nexters.external.repository.ExposureContentMarkdownRepository
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.service.category.ContentCategoryScoreService
import com.nexters.external.support.MarkdownValidator
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.JpaSort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ExposureContentService(
    private val exposureContentRepository: ExposureContentRepository,
    private val summaryRepository: SummaryRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val contentCategoryScoreService: ContentCategoryScoreService,
    private val exposureContentMarkdownRepository: ExposureContentMarkdownRepository,
) {
    private val logger = LoggerFactory.getLogger(ExposureContentService::class.java)

    @ExposureContentChanged
    @Transactional
    fun createExposureContentFromSummary(summaryId: Long): ExposureContent {
        val summary =
            summaryRepository
                .findById(summaryId)
                .orElseThrow { NoSuchElementException("Summary not found with ID: $summaryId") }

        require(!ContentSourceValidator.isInvalidSource(summary.content.content)) {
            "Cannot expose content with an invalid source placeholder: ${summary.content.id}"
        }

        // Check if exposure content already exists for this content
        val existingExposureContent = exposureContentRepository.findByContent(summary.content)

        if (existingExposureContent != null) {
            logger.info("Exposure content already exists for content ID: ${summary.content.id}")
            contentCategoryScoreService.recalculateForContent(summary.content)
            return existingExposureContent
        }

        // Get the most provocative keyword for this content
        val provocativeKeyword = findMostProvocativeKeyword(summary.content)

        // Get the most provocative headline from the summary
        val provocativeHeadline = findProvocativeHeadline(summary)

        // Create and save the exposure content
        val exposureContent =
            ExposureContent(
                content = summary.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summary.summarizedContent,
            )

        return exposureContentRepository.save(exposureContent).also {
            contentCategoryScoreService.recalculateForContent(summary.content)
        }
    }

    @ExposureContentChanged
    @Transactional
    fun setActiveSummaryAsExposureContent(summaryId: Long): ExposureContent {
        val summary =
            summaryRepository
                .findById(summaryId)
                .orElseThrow { NoSuchElementException("Summary not found with ID: $summaryId") }

        require(!ContentSourceValidator.isInvalidSource(summary.content.content)) {
            "Cannot expose content with an invalid source placeholder: ${summary.content.id}"
        }

        // Delete any existing exposure content for this content
        exposureContentRepository
            .findByContent(summary.content)
            ?.let {
                exposureContentRepository.deleteById(it.id!!)
                logger.info("Deleted existing exposure content ID: ${it.id} for content ID: $it.id")
            }

        // Get the most provocative keyword for this content
        val provocativeKeyword = findMostProvocativeKeyword(summary.content)

        // Create and save the new exposure content
        val exposureContent =
            ExposureContent(
                content = summary.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = summary.title,
                summaryContent = summary.summarizedContent,
            )

        return exposureContentRepository.save(exposureContent).also {
            contentCategoryScoreService.recalculateForContent(summary.content)
        }
    }

    private fun findMostProvocativeKeyword(content: Content): String {
        val keywordMappings = contentKeywordMappingRepository.findByContent(content)

        return if (keywordMappings.isNotEmpty()) {
            val tags = keywordMappings
                .map { it.keyword.name.trim().removePrefix("#") }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)
                .map { "#$it" }
            if (tags.isNotEmpty()) {
                tags.joinToString(" ")
            } else {
                "#테크트렌드 #개발인사이트"
            }
        } else {
            "#테크트렌드 #개발인사이트"
        }
    }

    private fun findProvocativeHeadline(summary: Summary): String {
        // In a real implementation, this might involve NLP or other analysis
        // For now, we'll just use the original title as the provocative headline
        return summary.title
    }

    fun getAllExposureContents(): List<ExposureContent> = exposureContentRepository.findAll()

    fun getAllExposureContentsWithPaging(
        lastSeenOffset: Long,
        pageable: Pageable
    ): Page<ExposureContent> = exposureContentRepository.findAllWithOffset(lastSeenOffset, pageable)

    @Transactional(readOnly = true)
    fun getExploreContentRows(
        lastSeenOffset: Long,
        limit: Int,
        direction: Sort.Direction = Sort.Direction.DESC,
        categoryIds: List<Long>? = null,
    ): List<ExploreContentRow> {
        val pageable = PageRequest.of(0, limit, registeredSort(direction))
        return when {
            lastSeenOffset == 0L && categoryIds == null -> exposureContentRepository.findExploreRows(pageable)
            lastSeenOffset == 0L && categoryIds != null -> exposureContentRepository.findExploreRowsByCategoryIds(categoryIds, pageable)
            direction.isAscending && categoryIds == null ->
                exposureContentRepository.findExploreRowsAfterAscending(
                    lastSeenOffset,
                    pageable
                )
            direction.isAscending && categoryIds != null ->
                exposureContentRepository.findExploreRowsAfterAscendingByCategoryIds(
                    lastSeenOffset,
                    categoryIds,
                    pageable
                )
            categoryIds == null -> exposureContentRepository.findExploreRowsAfter(lastSeenOffset, pageable)
            else -> exposureContentRepository.findExploreRowsAfterByCategoryIds(lastSeenOffset, categoryIds, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun getExploreContentRowsSortedByPublishedAt(
        lastSeenOffset: Long,
        limit: Int,
        direction: Sort.Direction = Sort.Direction.DESC,
        categoryIds: List<Long>? = null,
    ): List<ExploreContentRow> {
        val pageable = PageRequest.of(0, limit, publishedSort(direction))
        if (lastSeenOffset == 0L) {
            return when {
                categoryIds == null -> exposureContentRepository.findExploreRows(pageable)
                else -> exposureContentRepository.findExploreRowsByCategoryIds(categoryIds, pageable)
            }
        }
        val publishedAt =
            exposureContentRepository
                .findById(lastSeenOffset)
                .orElseThrow { NoSuchElementException("Exposure content not found: $lastSeenOffset") }
                .content.publishedAt
        return when {
            direction.isAscending && categoryIds == null ->
                exposureContentRepository.findExploreRowsAfterByPublishedAtAscending(
                    publishedAt,
                    pageable
                )
            direction.isAscending && categoryIds != null ->
                exposureContentRepository
                    .findExploreRowsAfterByPublishedAtAscendingByCategoryIds(
                        publishedAt,
                        categoryIds,
                        pageable
                    )
            categoryIds == null -> exposureContentRepository.findExploreRowsAfterByPublishedAt(publishedAt, pageable)
            else -> exposureContentRepository.findExploreRowsAfterByPublishedAtByCategoryIds(publishedAt, categoryIds, pageable)
        }
    }

    fun countByCategoryIds(categoryIds: List<Long>): Long = exposureContentRepository.countByCategoryIds(categoryIds)

    fun getAllExposureContentsPaged(pageable: Pageable): Page<ExposureContent> = exposureContentRepository.findAllPaged(pageable)

    fun getExposureContentsByKeywordPaged(
        keyword: String,
        pageable: Pageable
    ): Page<ExposureContent> = exposureContentRepository.findByProvocativeKeyword(keyword, pageable)

    fun getNoKeywordsCount(): Long = exposureContentRepository.countByNoKeywords()

    fun getExposureContentById(id: Long): ExposureContent =
        exposureContentRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("Exposure content not found with ID: $id") }

    fun getMarkdownByExposureContentId(exposureContentId: Long): com.nexters.external.entity.ExposureContentMarkdown =
        exposureContentMarkdownRepository.findByExposureContentId(exposureContentId)
            ?: throw NoSuchElementException("Markdown not found for exposure content ID: $exposureContentId")

    fun getExposureContentByContent(content: Content): ExposureContent? = exposureContentRepository.findByContent(content)

    @ExposureContentChanged
    @Transactional
    fun updateExposureContent(
        id: Long,
        provocativeKeyword: String,
        provocativeHeadline: String,
        summaryContent: String,
    ): ExposureContent {
        val existingContent = getExposureContentById(id)

        // Create a new instance with updated values
        // Since ExposureContent is immutable, we need to create a new instance
        val updatedContent =
            ExposureContent(
                id = existingContent.id,
                content = existingContent.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summaryContent,
                createdAt = existingContent.createdAt,
                updatedAt = LocalDateTime.now(),
            )

        return exposureContentRepository.save(updatedContent)
    }

    @ExposureContentChanged
    @Transactional
    fun deleteExposureContent(id: Long) {
        if (exposureContentRepository.existsById(id)) {
            exposureContentRepository.deleteById(id)
        } else {
            throw NoSuchElementException("Exposure content not found with ID: $id")
        }
    }

    @ExposureContentChanged
    @Transactional
    fun createOrUpdateExposureContent(
        content: Content,
        provocativeKeyword: String,
        provocativeHeadline: String,
        summaryContent: String
    ): ExposureContent {
        require(!ContentSourceValidator.isInvalidSource(content.content)) {
            "Cannot expose content with an invalid source placeholder: ${content.id}"
        }

        // Check if exposure content already exists for this content
        val existingExposureContent = exposureContentRepository.findByContent(content)

        if (existingExposureContent != null) {
            // Update existing exposure content
            val updatedContent =
                ExposureContent(
                    id = existingExposureContent.id,
                    content = existingExposureContent.content,
                    provocativeKeyword = provocativeKeyword,
                    provocativeHeadline = provocativeHeadline,
                    summaryContent = summaryContent,
                    createdAt = existingExposureContent.createdAt,
                    updatedAt = LocalDateTime.now()
                )

            logger.info("Updated existing exposure content for content ID: ${content.id}")
            return exposureContentRepository.save(updatedContent).also {
                contentCategoryScoreService.recalculateForContent(existingExposureContent.content)
            }
        } else {
            // Create new exposure content
            val newExposureContent =
                ExposureContent(
                    content = content,
                    provocativeKeyword = provocativeKeyword,
                    provocativeHeadline = provocativeHeadline,
                    summaryContent = summaryContent,
                )

            logger.info("Created new exposure content for content ID: ${content.id}")
            return exposureContentRepository.save(newExposureContent).also {
                contentCategoryScoreService.recalculateForContent(content)
            }
        }
    }

    fun getNotExposedRecommendationCandidatesByReservedKeywordIds(
        userId: Long,
        reservedKeywordIds: List<Long>,
        publishedFrom: LocalDate,
        limit: Int,
    ): List<ExposureContentRecommendationCandidateRow> =
        exposureContentRepository.findNotExposedRecommendationCandidatesByReservedKeywordIds(
            userId = userId,
            reservedKeywordIds = reservedKeywordIds,
            publishedFrom = publishedFrom,
            pageable = PageRequest.of(0, limit),
        )

    fun getNotExposedRecommendationCandidatesByContentProviderIds(
        userId: Long,
        contentProviderIds: List<Long>,
        publishedFrom: LocalDate,
        limit: Int,
    ): List<ExposureContentRecommendationCandidateRow> =
        exposureContentRepository.findNotExposedRecommendationCandidatesByContentProviderIds(
            userId = userId,
            contentProviderIds = contentProviderIds,
            publishedFrom = publishedFrom,
            pageable = PageRequest.of(0, limit),
        )

    fun getNotExposedRecommendationCandidatesByCategoryIds(
        userId: Long,
        categoryIds: List<Long>,
        publishedFrom: LocalDate,
        limit: Int,
    ): List<ExposureContentRecommendationCandidateRow> {
        if (categoryIds.isEmpty()) {
            return emptyList()
        }

        return exposureContentRepository.findNotExposedRecommendationCandidatesByCategoryScoreCategoryIds(
            userId = userId,
            categoryIds = categoryIds,
            publishedFrom = publishedFrom,
            pageable = PageRequest.of(0, limit),
        )
    }

    fun getNotExposedSemanticRecommendationCandidates(
        userId: Long,
        categoryIds: List<Long>,
        publishedFrom: LocalDate,
        limit: Int,
    ): List<ExposureContentRecommendationCandidateRow> {
        if (categoryIds.isEmpty() || limit <= 0) {
            return emptyList()
        }

        return exposureContentRepository.findNotExposedSemanticRecommendationCandidates(
            userId = userId,
            categoryIds = categoryIds,
            publishedFrom = publishedFrom,
            limit = limit,
        ).map { proj ->
            ExposureContentRecommendationCandidateRow(
                exposureContentId = proj.exposureContentId,
                contentId = proj.contentId,
                contentProviderId = proj.contentProviderId,
                contentProviderName = proj.contentProviderName,
                newsletterName = proj.newsletterName,
                publishedAt = proj.publishedAt,
                title = proj.title,
                provocativeHeadline = proj.provocativeHeadline,
                summaryContent = proj.summaryContent,
            )
        }
    }

    fun getExposureContentsByIdsPreservingOrder(exposureContentIds: List<Long>): List<ExposureContent> {
        if (exposureContentIds.isEmpty()) {
            return emptyList()
        }

        val exposureContentsById =
            exposureContentRepository
                .findByIdsWithContent(exposureContentIds)
                .associateBy { it.id!! }

        return exposureContentIds.mapNotNull { exposureContentsById[it] }
    }

    fun getArchiveSnapshotsByIdsPreservingOrder(exposureContentIds: List<Long>,): List<DailyContentArchive.ExposureContentSnapshot> {
        if (exposureContentIds.isEmpty()) {
            return emptyList()
        }

        val archiveRowsById =
            exposureContentRepository
                .findArchiveRowsByIds(exposureContentIds)
                .associateBy { it.exposureContentId }

        val markdownsByExposureContentId =
            exposureContentMarkdownRepository
                .findAllByExposureContentIdIn(exposureContentIds)
                .associateBy { it.exposureContentId }

        return exposureContentIds.mapNotNull { exposureContentId ->
            val row = archiveRowsById[exposureContentId] ?: return@mapNotNull null
            val markdown = markdownsByExposureContentId[exposureContentId]?.markdownContent
            val readingTime = MarkdownValidator.estimateReadingTimeMinutes(markdown)
            row.toArchiveSnapshot(estimatedReadingTime = readingTime)
        }
    }

    fun findSimilarContentPairs(
        contentIds: Collection<Long>,
        minSimilarity: Double = 0.80,
    ): Map<Long, Map<Long, Double>> {
        val distinctContentIds = contentIds.distinct()
        if (distinctContentIds.size < 2) {
            return emptyMap()
        }

        return try {
            val pairs = exposureContentRepository.findSimilarContentPairs(distinctContentIds, minSimilarity)
            val similarityMap = mutableMapOf<Long, MutableMap<Long, Double>>()
            for (pair in pairs) {
                similarityMap.computeIfAbsent(pair.contentId1) { mutableMapOf() }[pair.contentId2] = pair.similarity
                similarityMap.computeIfAbsent(pair.contentId2) { mutableMapOf() }[pair.contentId1] = pair.similarity
            }
            similarityMap
        } catch (e: Exception) {
            logger.warn("콘텐츠 간 임베딩 유사도 쌍 조회 중 오류 발생 (어휘적 중복 제거로 폴백): ${e.message}")
            emptyMap()
        }
    }

    fun countAllExposureContents(): Long = exposureContentRepository.count()

    private fun ExposureContentArchiveRow.toArchiveSnapshot(estimatedReadingTime: Int = 2): DailyContentArchive.ExposureContentSnapshot =
        DailyContentArchive.ExposureContentSnapshot(
            id = exposureContentId,
            content =
                DailyContentArchive.ContentSnapshot(
                    id = contentId,
                    originalUrl = contentUrl,
                    imageUrl = imageUrl,
                    newsletterName = newsletterName,
                    contentProvider =
                        contentProviderId?.let { id ->
                            DailyContentArchive.ContentProviderSnapshot(
                                id = id,
                                language = contentProviderLanguage,
                                type = contentProviderType,
                            )
                        },
                ),
            provocativeKeyword = provocativeKeyword,
            provocativeHeadline = provocativeHeadline,
            summaryContent = summaryContent,
            createdAt = createdAt,
            updatedAt = updatedAt,
            estimatedReadingTime = estimatedReadingTime,
        )

    companion object {
        private fun registeredSort(direction: Sort.Direction): Sort = JpaSort.unsafe(direction, "e.id")

        private fun publishedSort(direction: Sort.Direction): Sort =
            JpaSort
                .unsafe(direction, "c.publishedAt")
                .and(JpaSort.unsafe(direction, "e.id"))
    }
}
