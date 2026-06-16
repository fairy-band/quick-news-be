package com.nexters.newsletter.resolver

import com.nexters.external.service.CategoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PossibleContentsResolver(
    candidateSources: List<CandidateSource>,
    private val categoryService: CategoryService,
) {
    private val candidateSources = candidateSources.sortedBy { it.order }

    fun resolveCandidatePoolByCategoryIds(
        userId: Long,
        categoryIds: List<Long>,
    ): CandidatePool {
        if (categoryIds.isEmpty() || candidateSources.isEmpty()) {
            return CandidatePool(emptyList(), CandidateRecencyWindow.DAYS_30)
        }

        val mergedCandidates = linkedMapOf<Long, CandidatePoolItem>()
        var expandedWindow = CandidateRecencyWindow.DAYS_30
        val today = LocalDate.now()
        val context = createCandidateSourceContext(categoryIds)

        for (window in RECENCY_WINDOWS) {
            expandedWindow = window
            fetchWindowCandidates(
                userId = userId,
                context = context,
                window = window,
                today = today,
                mergedCandidates = mergedCandidates,
            )

            if (mergedCandidates.size >= TARGET_POOL_SIZE) {
                break
            }
        }

        val candidates =
            mergedCandidates
                .values
                .sortedWith(CANDIDATE_POOL_COMPARATOR)
                .take(MAX_POOL_SIZE)

        logger.debug(
            "가능한 콘텐츠 풀 생성 완료. userId: {}, categoryIds: {}, sourceCount: {}, window: {}, candidates: {}",
            userId,
            categoryIds,
            candidateSources.size,
            expandedWindow,
            candidates.size,
        )

        return CandidatePool(
            candidates = candidates,
            expandedWindow = expandedWindow,
        )
    }

    private fun fetchWindowCandidates(
        userId: Long,
        context: CandidateSourceContext,
        window: CandidateRecencyWindow,
        today: LocalDate,
        mergedCandidates: MutableMap<Long, CandidatePoolItem>,
    ) {
        val publishedFrom = window.publishedFrom(today)

        for (source in candidateSources) {
            source
                .fetch(
                    CandidateSourceRequest(
                        userId = userId,
                        context = context,
                        publishedFrom = publishedFrom,
                        limit = source.defaultLimit * window.limitMultiplier,
                        window = window,
                    ),
                ).forEach { seed ->
                    mergedCandidates.merge(seed)
                }

            if (mergedCandidates.size >= TARGET_POOL_SIZE) {
                break
            }
        }
    }

    private fun createCandidateSourceContext(categoryIds: List<Long>): CandidateSourceContext =
        CandidateSourceContext(
            categoryIds = categoryIds,
            reservedKeywordIdsLazy =
                lazy(LazyThreadSafetyMode.NONE) {
                    categoryService
                        .getKeywordsByCategoryIds(categoryIds)
                        .mapNotNull { it.id }
                        .distinct()
                },
            contentProviderIdsLazy =
                lazy(LazyThreadSafetyMode.NONE) {
                    categoryService
                        .getContentProvidersByCategoryIds(categoryIds)
                        .mapNotNull { it.id }
                        .distinct()
                },
        )

    private fun MutableMap<Long, CandidatePoolItem>.merge(seed: CandidateSeed) {
        val exposureContentId = seed.candidate.exposureContentId
        val existing = this[exposureContentId]

        this[exposureContentId] =
            if (existing == null) {
                CandidatePoolItem(
                    candidate = seed.candidate,
                    signals = deduplicateSignals(seed.signals),
                )
            } else {
                existing.copy(
                    signals = deduplicateSignals(existing.signals + seed.signals),
                )
            }
    }

    private fun deduplicateSignals(signals: List<CandidateSourceSignal>): List<CandidateSourceSignal> =
        signals
            .groupBy { it.source }
            .mapNotNull { (_, sourceSignals) ->
                sourceSignals.maxWithOrNull(
                    compareBy<CandidateSourceSignal> { it.confidence }
                        .thenBy { it.score },
                )
            }.sortedWith(
                compareByDescending<CandidateSourceSignal> { it.confidence }
                    .thenByDescending { it.score },
            )

    companion object {
        private val logger = LoggerFactory.getLogger(PossibleContentsResolver::class.java)
        private val RECENCY_WINDOWS =
            listOf(
                CandidateRecencyWindow.DAYS_30,
                CandidateRecencyWindow.DAYS_90,
                CandidateRecencyWindow.DAYS_365,
                CandidateRecencyWindow.ALL,
            )
        private const val TARGET_POOL_SIZE = 120
        private const val MAX_POOL_SIZE = 200
        private val CANDIDATE_POOL_COMPARATOR =
            compareByDescending<CandidatePoolItem> { it.candidate.publishedAt }
                .thenByDescending { it.signals.sumOf { signal -> signal.confidence } }
                .thenByDescending { it.signals.sumOf { signal -> signal.score } }
                .thenByDescending { it.candidate.exposureContentId }
    }
}
