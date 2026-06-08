package com.nexters.newsletter.resolver

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentProviderService
import com.nexters.external.service.DailyContentArchiveService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

@Service
class DailyContentArchiveResolver(
    private val userService: UserService,
    private val categoryService: CategoryService,
    private val dailyContentArchiveService: DailyContentArchiveService,
    private val possibleContentsResolver: PossibleContentsResolver,
    private val contentProviderService: ContentProviderService,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val exposureContentService: ExposureContentService,
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
        // cache hit 인 경우 락 진입 없이 lock-free 로 반환
        dailyContentArchiveService.findByDateAndUserId(userId, date)?.let { return it }

        lock.lock()
        try {
            // 락 대기 중 다른 스레드가 채워 넣었을 수 있으므로 한 번 더 확인
            dailyContentArchiveService.findByDateAndUserId(userId, date)?.let { return it }

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
        val candidatePool = possibleContentsResolver.resolveCandidatePoolByCategoryIds(userId, categoryIds)
        val possibleContents = candidatePool.candidates.map { it.candidate }
        val candidateSignalsByExposureContentId =
            candidatePool.candidates.associate { poolItem ->
                poolItem.candidate.exposureContentId to poolItem.signals
            }
        val keywordWeights = categoryService.getKeywordWeightsByCategoryIds(categoryIds)

        // TODO: entity 의존성 없는 구조로 변경 필요
        return resolveTodayContents(
            userId = userId,
            possibleContents = possibleContents,
            candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
            keywordWeightsByKeyword = keywordWeights,
            categoryIds = categoryIds,
        )
    }

    private fun resolveTodayContents(
        userId: Long,
        possibleContents: List<ExposureContentRecommendationCandidateRow>,
        candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        categoryIds: List<Long>,
    ): List<ExposureContent> {
        // 개수가 충분치 않으면 바로 반환
        if (possibleContents.size <= MAX_CONTENT_SIZE) {
            logger.error(
                "추천할 콘텐츠가 부족합니다. userId: {}, 가능한 콘텐츠 수: {}, 최대 콘텐츠 크기: {}",
                userId,
                possibleContents.size,
                MAX_CONTENT_SIZE,
            )
            return fetchExposureContents(possibleContents)
        }

        val contentIds = possibleContents.map { it.contentId }
        val mappings = contentKeywordMappingRepository.findKeywordsByContentIds(contentIds)
        val keywordsByContentId = mappings.groupBy({ it.contentId }, { it.keyword })

        // multiplier 루프 동안 동일 입력으로 반복 호출되는 DB 쿼리를 1회로 줄임
        val categoryMatchWeights = getCategoryMatchWeights(possibleContents, categoryIds)

        // 카테고리에 해당하는 키워드 가중치 맵 생성 (기본 가중치 1.0 사용)
        val contentSources =
            createRecommendSources(
                possibleContents = possibleContents,
                candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
                keywordWeightsByKeyword = keywordWeightsByKeyword,
                keywordsByContentId = keywordsByContentId,
                multiplier = 1.0,
                categoryIds = categoryIds,
                categoryMatchWeights = categoryMatchWeights,
            )

        val scoredCandidates = scoreCandidates(contentSources)

        // 1단계: recommendScore > 0인 컨텐츠들 필터링
        val positiveScoreContents =
            scoredCandidates
                .filter { it.recommendScore > 0 }
                .map { it.candidate }

        // 2단계: 선택된 컨텐츠 내에서 발행처 다양성 조정
        if (positiveScoreContents.size >= MAX_CONTENT_SIZE) {
            val candidatePublisherCounts = mutableMapOf<String, Int>()
            val maxPerPublisher = MAX_CONTENT_SIZE - 1

            val scoredContents =
                positiveScoreContents
                    // 상위 컨텐츠 후보(N*N배)를 대상으로 발행처별 선택 횟수 계산
                    .take(MAX_CONTENT_SIZE * MAX_CONTENT_SIZE)
                    .map { candidate ->
                        val source = contentSources[candidate]!!
                        val publisherId = candidate.publisherName
                        val publisherDuplicateCandidateCount = candidatePublisherCounts.getOrDefault(publisherId, 0)

                        val adjustedSource =
                            source.copy(
                                publisherDuplicateCandidateCount = publisherDuplicateCandidateCount,
                            )

                        candidatePublisherCounts[publisherId] = publisherDuplicateCandidateCount + 1

                        ScoredCandidate(
                            candidate = candidate,
                            recommendScore = calculator.calculate(adjustedSource).recommendScore,
                        )
                    }.sortedWith(SCORED_CANDIDATE_COMPARATOR)
                    .map { it.candidate }

            // 발행처당 최대 N-1개로 제한
            val selectedPublisherCounts = mutableMapOf<String, Int>()
            val diversifiedContents =
                scoredContents
                    .filter { candidate ->
                        val publisherId = candidate.publisherName
                        val count = selectedPublisherCounts.getOrDefault(publisherId, 0)
                        if (count < maxPerPublisher) {
                            selectedPublisherCounts[publisherId] = count + 1
                            true
                        } else {
                            false
                        }
                    }.take(MAX_CONTENT_SIZE)

            return fetchExposureContents(diversifiedContents)
        }

        // 3단계: 부족하면 가중치를 2배, 3배, 4배로 늘려가며 추가 컨텐츠 찾기
        // 순서 보존 + O(1) contains 위해 LinkedHashSet 사용 (multiplier 루프에서 중복 추가 방지)
        val selectedContents = LinkedHashSet<ExposureContentRecommendationCandidateRow>(positiveScoreContents)
        val multipliers = listOf(2.0, 3.0, 4.0)

        for (multiplier in multipliers) {
            if (selectedContents.size >= MAX_CONTENT_SIZE) {
                break
            }

            val amplifiedRecommendSources =
                createRecommendSources(
                    possibleContents = possibleContents,
                    candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
                    keywordWeightsByKeyword = keywordWeightsByKeyword,
                    keywordsByContentId = keywordsByContentId,
                    multiplier = multiplier,
                    categoryIds = categoryIds,
                    categoryMatchWeights = categoryMatchWeights,
                )

            // 재계산된 결과로 정렬하고 기존에 선택되지 않은 positive score 컨텐츠들 필터링
            val amplifiedContents =
                scoreCandidates(amplifiedRecommendSources)
                    .filter { it.candidate !in selectedContents }
                    .filter { it.recommendScore > 0 }
                    .map { it.candidate }

            val additionalNeeded = MAX_CONTENT_SIZE - selectedContents.size
            selectedContents.addAll(amplifiedContents.take(additionalNeeded))
        }

        return fetchExposureContents(selectedContents.take(MAX_CONTENT_SIZE))
    }

    /**
     * 가중치를 적용해서 추천 계산을 위한 소스를 생성합니다.
     */
    private fun createRecommendSources(
        possibleContents: List<ExposureContentRecommendationCandidateRow>,
        candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        keywordsByContentId: Map<Long, List<ReservedKeyword>>,
        multiplier: Double,
        categoryIds: List<Long>,
        categoryMatchWeights: Map<Pair<Long, Long>, Double>,
    ): Map<ExposureContentRecommendationCandidateRow, RecommendCalculateSource> =
        possibleContents.associateWith { candidate ->
            val contentKeywords = keywordsByContentId[candidate.contentId] ?: emptyList()
            RecommendCalculateSource(
                title = candidate.title,
                provocativeHeadline = candidate.provocativeHeadline,
                summaryContent = candidate.summaryContent,
                newsletterName = candidate.newsletterName,
                contentProviderName = candidate.contentProviderName,
                keywordNames = contentKeywords.map { it.name },
                candidateSignals = candidateSignalsByExposureContentId[candidate.exposureContentId] ?: emptyList(),
                positiveKeywordSources = extractPositiveKeywordSources(contentKeywords, keywordWeightsByKeyword, multiplier),
                negativeKeywordSources = extractNegativeKeywordSources(contentKeywords, keywordWeightsByKeyword),
                publishedDate = candidate.publishedAt,
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = calculateCategoryMatchBonus(candidate.contentProviderId, categoryIds, categoryMatchWeights),
            )
        }

    /**
     * ContentProvider-Category 매핑 가중치를 조회합니다.
     */
    private fun getCategoryMatchWeights(
        possibleContents: List<ExposureContentRecommendationCandidateRow>,
        categoryIds: List<Long>,
    ): Map<Pair<Long, Long>, Double> {
        val contentProviderIds = possibleContents.mapNotNull { it.contentProviderId }.distinct()
        return contentProviderService.getCategoryMatchWeights(contentProviderIds, categoryIds)
    }

    /**
     * 양수 키워드 소스를 추출합니다.
     */
    private fun extractPositiveKeywordSources(
        keywords: List<ReservedKeyword>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        multiplier: Double,
    ): List<PositiveKeywordSource> =
        keywords
            .filter { keyword -> (keywordWeightsByKeyword[keyword] ?: 0.0) > 0 }
            .map { keyword ->
                PositiveKeywordSource(
                    weight = (keywordWeightsByKeyword[keyword] ?: 0.0) * multiplier,
                    keywordName = keyword.name,
                )
            }

    /**
     * 음수 키워드 소스를 추출합니다.
     */
    private fun extractNegativeKeywordSources(
        keywords: List<ReservedKeyword>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    ): List<NegativeKeywordSource> =
        keywords
            .filter { keyword -> (keywordWeightsByKeyword[keyword] ?: 0.0) < 0 }
            .map { keyword ->
                NegativeKeywordSource(
                    weight = keywordWeightsByKeyword[keyword] ?: 0.0,
                    keywordName = keyword.name,
                )
            }

    /**
     * 카테고리 매칭 보너스를 계산합니다.
     * 여러 카테고리에 매핑된 경우 가중치를 합산합니다.
     */
    private fun calculateCategoryMatchBonus(
        contentProviderId: Long?,
        categoryIds: List<Long>,
        categoryMatchWeights: Map<Pair<Long, Long>, Double>,
    ): Double {
        contentProviderId ?: return 0.0

        return categoryIds.sumOf { categoryId ->
            categoryMatchWeights[contentProviderId to categoryId] ?: 0.0
        }
    }

    private fun scoreCandidates(
        contentSources: Map<ExposureContentRecommendationCandidateRow, RecommendCalculateSource>,
    ): List<ScoredCandidate> =
        contentSources
            .map { (candidate, source) ->
                ScoredCandidate(
                    candidate = candidate,
                    recommendScore = calculator.calculate(source).recommendScore,
                )
            }.sortedWith(SCORED_CANDIDATE_COMPARATOR)

    private fun fetchExposureContents(candidates: List<ExposureContentRecommendationCandidateRow>): List<ExposureContent> =
        exposureContentService.getExposureContentsByIdsPreservingOrder(
            candidates.map { it.exposureContentId },
        )

    private val ExposureContentRecommendationCandidateRow.publisherName: String
        get() = contentProviderName ?: newsletterName

    /**
     * reservedKeywords를 배치로 미리 로드하여 N+1 문제 방지
     */
    private fun preloadReservedKeywords(contentIds: List<Long>) {
        // Hibernate가 배치로 로드하도록 트리거
        // 실제 구현은 @BatchSize 어노테이션과 함께 동작
        logger.debug("Preloading reserved keywords for {} contents", contentIds.size)
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
        private val SCORED_CANDIDATE_COMPARATOR =
            compareByDescending<ScoredCandidate> { it.recommendScore }
                .thenByDescending { it.candidate.publishedAt }
                .thenByDescending { it.candidate.exposureContentId }
    }
}

private data class ScoredCandidate(
    val candidate: ExposureContentRecommendationCandidateRow,
    val recommendScore: Double,
)

class RefreshNotAvailableException(
    message: String
) : RuntimeException(message)
