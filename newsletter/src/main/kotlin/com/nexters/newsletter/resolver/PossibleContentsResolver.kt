package com.nexters.newsletter.resolver

import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ExposureContentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PossibleContentsResolver(
    private val categoryService: CategoryService,
    private val exposureContentService: ExposureContentService,
) {
    fun resolvePossibleContentsByCategoryIds(
        userId: Long,
        categoryIds: List<Long>,
    ): List<ExposureContentRecommendationCandidateRow> {
        val keywords = categoryService.getKeywordsByCategoryIds(categoryIds)
        val contentsByKeywords = resolvePossibleContentsByKeywords(userId, keywords)

        val contentProviders = categoryService.getContentProvidersByCategoryIds(categoryIds)
        val contentsByProviders = resolvePossibleContentsByProviders(userId, contentProviders)

        val allContents = (contentsByKeywords + contentsByProviders).distinctBy { it.exposureContentId }

        logger.debug(
            "가능한 콘텐츠 조회 완료. userId: {}, 키워드 수: {}, ContentProvider 수: {}, 총 콘텐츠 수: {}",
            userId,
            keywords.size,
            contentProviders.size,
            allContents.size,
        )

        return allContents
    }

    private fun resolvePossibleContentsByKeywords(
        userId: Long,
        reservedKeywords: List<ReservedKeyword>,
    ): List<ExposureContentRecommendationCandidateRow> {
        val reservedKeywordIds = reservedKeywords.map { it.id!! }
        return exposureContentService.getNotExposedRecommendationCandidatesByReservedKeywordIds(userId, reservedKeywordIds)
    }

    private fun resolvePossibleContentsByProviders(
        userId: Long,
        contentProviders: List<*>,
    ): List<ExposureContentRecommendationCandidateRow> {
        val contentProviderIds = contentProviders.mapNotNull { (it as? ContentProvider)?.id }
        return if (contentProviderIds.isNotEmpty()) {
            exposureContentService.getNotExposedRecommendationCandidatesByContentProviderIds(userId, contentProviderIds)
        } else {
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PossibleContentsResolver::class.java)
    }
}
