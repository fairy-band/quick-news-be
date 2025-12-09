package com.nexters.newsletter.resolver

import com.nexters.external.entity.Content
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentProviderCategoryMappingRepository
import com.nexters.external.service.CategoryService
import com.nexters.external.service.DailyContentArchiveService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.SortedMap
import java.util.concurrent.locks.ReentrantLock

@Service
class DailyContentArchiveResolver(
    private val userService: UserService,
    private val categoryService: CategoryService,
    private val exposureContentService: ExposureContentService,
    private val dailyContentArchiveService: DailyContentArchiveService,
    private val possibleContentsResolver: PossibleContentsResolver,
    private val contentProviderCategoryMappingRepository: ContentProviderCategoryMappingRepository,
) {
    private val calculator = RecommendScoreCalculator()
    private val lock = ReentrantLock() // instance가 늘어나면 락 구현체 변경 필요, 분산 락으로 전환

    fun resolveTodayCategoryContents(categoryId: Long): List<ExposureContent> =
        resolveTodayContents(
            ARBITRARY_USER_ID,
            listOf(categoryId),
        )

    fun getTodayContentArchive(
        userId: Long,
        date: LocalDate = LocalDate.now(),
    ): DailyContentArchive? = dailyContentArchiveService.findByDateAndUserId(userId, date)

    @Transactional
    fun resolveTodayContentArchive(
        userId: Long,
        date: LocalDate = LocalDate.now(),
    ): DailyContentArchive {
        lock.lock()
        try {
            val contentArchive = dailyContentArchiveService.findByDateAndUserId(userId, date)

            if (contentArchive != null) {
                return contentArchive
            }

            val user = userService.getUserById(userId)
            val userCategories = user.categories.map { it.id!! }
            val categoryIds =
                userCategories.ifEmpty {
                    categoryService.getAllCategories().map { it.id!! }
                }

            val contents = resolveTodayContents(userId, categoryIds)

            val dailyContentArchive =
                DailyContentArchive(
                    user = DailyContentArchive.UserSnapshot.from(user),
                    date = date,
                    exposureContents = contents,
                )

            return dailyContentArchiveService.saveWithHistory(dailyContentArchive)
        } finally {
            lock.unlock()
        }
    }

    @Transactional
    fun refreshTodayArchives(
        userId: Long,
        date: LocalDate = LocalDate.now()
    ) {
        lock.lock()
        try {
            val archive = dailyContentArchiveService.findByDateAndUserId(userId, date) ?: return

            if (!dailyContentArchiveService.isRefreshAvailable(userId, date)) {
                throw RefreshNotAvailableException("이미 새로고침을 사용했습니다. 하루에 한 번만 가능합니다.")
            }

            dailyContentArchiveService.deleteByDateAndUserId(userId, date)
        } finally {
            lock.unlock()
        }
    }

    private fun resolveTodayContents(
        userId: Long,
        categoryIds: List<Long>,
    ): List<ExposureContent> {
        // TODO: 1차 MVP 유저 정보가 필요할지?
        val possibleContents = possibleContentsResolver.resolvePossibleContentsByCategoryIds(userId, categoryIds)
        val keywordWeights = categoryService.getKeywordWeightsByCategoryIds(categoryIds)

        // TODO: entity 의존성 없는 구조로 변경 필요
        val contents =
            resolveTodayContents(
                userId = userId,
                possibleContents = possibleContents,
                keywordWeightsByKeyword = keywordWeights,
                categoryIds = categoryIds,
            )

        // Convert Content objects to ExposureContent objects
        return contents.mapNotNull { content -> exposureContentService.getExposureContentByContent(content) }
    }

    private fun resolveTodayContents(
        userId: Long,
        possibleContents: List<Content>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        categoryIds: List<Long>,
    ): List<Content> {
        // 개수가 충분치 않으면 바로 반환
        if (possibleContents.size <= MAX_CONTENT_SIZE) {
            logger.error(
                "추천할 콘텐츠가 부족합니다. userId: {}, 가능한 콘텐츠 수: {}, 최대 콘텐츠 크기: {}",
                userId,
                possibleContents.size,
                MAX_CONTENT_SIZE,
            )
            return possibleContents
        }

        // 카테고리에 해당하는 키워드 가중치 맵 생성 (기본 가중치 1.0 사용)
        val contentSources =
            createRecommendSources(
                possibleContents = possibleContents,
                keywordWeightsByKeyword = keywordWeightsByKeyword,
                multiplier = 1.0,
                categoryIds = categoryIds,
            )

        val sortedSources: SortedMap<Content, RecommendCalculateSource> =
            contentSources.toSortedMap { a, b ->
                calculator
                    .calculate(contentSources[b]!!)
                    .recommendScore
                    .compareTo(
                        calculator.calculate(contentSources[a]!!).recommendScore,
                    )
            }

        // 1단계: recommendScore > 0인 컨텐츠들 필터링
        val positiveScoreContents =
            sortedSources.entries
                .filter { (content, source) ->
                    calculator.calculate(source).recommendScore > 0
                }.map { it.key }

        // 2단계: 선택된 컨텐츠 내에서 발행처 다양성 조정
        if (positiveScoreContents.size >= MAX_CONTENT_SIZE) {
            val candidatePublisherCounts = mutableMapOf<String, Int>()
            val maxPerPublisher = MAX_CONTENT_SIZE - 1

            val scoredContents =
                positiveScoreContents
                    // 상위 컨텐츠 후보(N*N배)를 대상으로 발행처별 선택 횟수 계산
                    .take(MAX_CONTENT_SIZE * MAX_CONTENT_SIZE)
                    .map { content ->
                        val source = contentSources[content]!!
                        val publisherId = content.contentProvider?.name ?: content.newsletterName
                        val publisherDuplicateCandidateCount = candidatePublisherCounts.getOrDefault(publisherId, 0)

                        val adjustedSource =
                            RecommendCalculateSource(
                                source.positiveKeywordSources,
                                source.negativeKeywordSources,
                                source.publishedDate,
                                publisherDuplicateCandidateCount
                            )

                        candidatePublisherCounts[publisherId] = publisherDuplicateCandidateCount + 1

                        content to calculator.calculate(adjustedSource).recommendScore
                    }.sortedByDescending { it.second }
                    .map { it.first }

            // 발행처당 최대 N-1개로 제한
            val selectedPublisherCounts = mutableMapOf<String, Int>()
            val diversifiedContents =
                scoredContents
                    .filter { content ->
                        val publisherId = content.contentProvider?.name ?: content.newsletterName
                        val count = selectedPublisherCounts.getOrDefault(publisherId, 0)
                        if (count < maxPerPublisher) {
                            selectedPublisherCounts[publisherId] = count + 1
                            true
                        } else {
                            false
                        }
                    }.take(MAX_CONTENT_SIZE)

            return diversifiedContents
        }

        // 3단계: 부족하면 가중치를 2배, 3배, 4배로 늘려가며 추가 컨텐츠 찾기
        val selectedContents = positiveScoreContents.toMutableList()
        val multipliers = listOf(2.0, 3.0, 4.0)

        for (multiplier in multipliers) {
            if (selectedContents.size >= MAX_CONTENT_SIZE) {
                break
            }

            val amplifiedRecommendSources =
                createRecommendSources(
                    possibleContents = possibleContents,
                    keywordWeightsByKeyword = keywordWeightsByKeyword,
                    multiplier = multiplier,
                    categoryIds = categoryIds,
                )

            // 재계산된 결과로 정렬하고 기존에 선택되지 않은 positive score 컨텐츠들 필터링
            val amplifiedSortedSources: SortedMap<Content, RecommendCalculateSource> =
                amplifiedRecommendSources.toSortedMap { a, b ->
                    calculator
                        .calculate(amplifiedRecommendSources[b]!!)
                        .recommendScore
                        .compareTo(
                            calculator.calculate(amplifiedRecommendSources[a]!!).recommendScore,
                        )
                }

            val amplifiedContents =
                amplifiedSortedSources.entries
                    .filter { (content, source) -> !selectedContents.contains(content) }
                    .filter { (content, source) -> calculator.calculate(source).recommendScore > 0 }
                    .map { it.key }

            val additionalNeeded = MAX_CONTENT_SIZE - selectedContents.size
            selectedContents.addAll(amplifiedContents.take(additionalNeeded))
        }

        return selectedContents.take(MAX_CONTENT_SIZE)
    }

    /**
     * 가중치를 적용해서 추천 계산을 위한 소스를 생성합니다.
     */
    private fun createRecommendSources(
        possibleContents: List<Content>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        multiplier: Double,
        categoryIds: List<Long>,
    ): Map<Content, RecommendCalculateSource> {
        // 1단계: ContentProvider-Category 매핑 정보 조회
        val contentProviderIds =
            possibleContents
                .mapNotNull { it.contentProvider?.id }
                .distinct()

        val mappings =
            if (contentProviderIds.isNotEmpty()) {
                contentProviderCategoryMappingRepository
                    .findByContentProviderIdInAndCategoryIdIn(contentProviderIds, categoryIds)
                    .groupBy { it.contentProvider.id to it.category.id }
                    .mapValues { it.value.first().weight }
            } else {
                emptyMap()
            }

        // 2단계: Content별로 categoryMatchBonus 계산
        return possibleContents.associateWith { content ->
            // 컨텐츠의 키워드 중 카테고리-키워드 가중치가 있는 것만 필터링
            val relevantKeywords =
                content.reservedKeywords.filter { keyword ->
                    keywordWeightsByKeyword[keyword] != null
                }

            val positiveKeywords =
                relevantKeywords.filter { keyword ->
                    keywordWeightsByKeyword[keyword]!! > 0
                }

            val negativeKeywords =
                relevantKeywords.filter { keyword ->
                    keywordWeightsByKeyword[keyword]!! < 0
                }

            // 카테고리 매칭 보너스 계산 (여러 카테고리에 매핑된 경우 가중치 합산)
            val categoryMatchBonus =
                if (content.contentProvider != null) {
                    categoryIds.sumOf { categoryId ->
                        mappings[content.contentProvider!!.id to categoryId] ?: 0.0
                    }
                } else {
                    0.0
                }

            RecommendCalculateSource(
                positiveKeywords.map { PositiveKeywordSource((keywordWeightsByKeyword[it] ?: 0.0) * multiplier) },
                negativeKeywords.map { NegativeKeywordSource(keywordWeightsByKeyword[it] ?: 0.0) },
                content.publishedAt,
                0,
                categoryMatchBonus,
            )
        }
    }

    /**
     * 카테고리에 설정된 음수 키워드 목록을 가져옵니다.
     */
    fun getNegativeKeywords(categoryIds: List<Long>): List<ReservedKeyword> {
        val categoryKeywordWeights = categoryService.getKeywordWeightsByCategoryIds(categoryIds)

        return categoryKeywordWeights.entries
            .filter { it.value < 0 }
            .map { it.key }
            .toList()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DailyContentArchiveResolver::class.java)
        private const val MAX_CONTENT_SIZE = 6
        private const val ARBITRARY_USER_ID = 1L // 임시로 사용되는 유저 ID
    }
}

class RefreshNotAvailableException(
    message: String
) : RuntimeException(message)
