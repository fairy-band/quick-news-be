package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val recommendationCandidateSelectorLogger = LoggerFactory.getLogger(RecommendationCandidateSelector::class.java)
private const val SLOW_SELECTION_LOG_THRESHOLD_MS = 500L

@Component
class RecommendationCandidateSelector(
    private val scoringSourceFactory: CandidateScoringSourceFactory,
    private val ranker: RecommendationCandidateRanker,
    private val publisherDiversityPolicy: PublisherDiversityPolicy,
) {
    fun select(request: RecommendationCandidateSelectionRequest): List<ExposureContentRecommendationCandidateRow> {
        if (request.candidates.isEmpty() || request.limit <= 0) {
            return emptyList()
        }

        val trace = CandidateSelectionTrace()
        val context =
            trace.measure("createContext") {
                scoringSourceFactory.createContext(
                    candidates = request.candidates,
                    candidateSignalsByExposureContentId = request.candidateSignalsByExposureContentId,
                    keywordWeightsByKeyword = request.keywordWeightsByKeyword,
                    categoryIds = request.categoryIds,
                )
            }
        val filteredContext = trace.measure("filterByCategoryFit") { context.filterByCategoryFit() }
        if (filteredContext.candidates.isEmpty()) {
            trace.logIfSlow(request, context, filteredContext, selectedCount = 0, fallbackRounds = 0)
            return emptyList()
        }
        if (filteredContext.candidates.size < context.candidates.size) {
            recommendationCandidateSelectorLogger.debug(
                "카테고리 적합도 필터 적용. categoryIds: {}, before: {}, after: {}",
                request.categoryIds,
                context.candidates.size,
                filteredContext.candidates.size,
            )
        }
        val scoringContext =
            trace.measure("loadScoringFeatures") {
                scoringSourceFactory.loadScoringFeatures(filteredContext)
            }

        val sourcesByCandidate =
            trace.measure("createSources") {
                scoringSourceFactory.createSources(scoringContext, multiplier = 1.0)
            }
        val scoredCandidates = trace.measure("rankCandidates") { ranker.rank(sourcesByCandidate) }
        val positiveScoreCandidates =
            scoredCandidates
                .filter { it.recommendScore > 0 }
                .map { it.candidate }

        if (positiveScoreCandidates.size >= request.limit) {
            val selectedCandidates =
                trace.measure("publisherDiversity") {
                    publisherDiversityPolicy.apply(
                        candidates = positiveScoreCandidates,
                        sourcesByCandidate = sourcesByCandidate,
                        limit = request.limit,
                    )
                }
            trace.logIfSlow(
                request = request,
                context = context,
                filteredContext = filteredContext,
                selectedCount = selectedCandidates.size,
                fallbackRounds = 0,
            )
            return selectedCandidates
        }

        val selectedCandidates = LinkedHashSet<ExposureContentRecommendationCandidateRow>(positiveScoreCandidates)
        var fallbackRounds = 0

        for (multiplier in FALLBACK_MULTIPLIERS) {
            if (selectedCandidates.size >= request.limit) {
                break
            }

            fallbackRounds++
            val amplifiedSourcesByCandidate =
                trace.measureAccumulated("fallbackCreateSources") {
                    scoringSourceFactory.createSources(scoringContext, multiplier)
                }
            val amplifiedScoredCandidates =
                trace.measureAccumulated("fallbackRankCandidates") {
                    ranker.rank(amplifiedSourcesByCandidate)
                }
            val amplifiedCandidates =
                amplifiedScoredCandidates
                    .filter { it.candidate !in selectedCandidates }
                    .filter { it.recommendScore > 0 }
                    .map { it.candidate }
            val additionalNeeded = request.limit - selectedCandidates.size
            selectedCandidates.addAll(amplifiedCandidates.take(additionalNeeded))
        }

        return selectedCandidates
            .take(request.limit)
            .also {
                trace.logIfSlow(
                    request = request,
                    context = context,
                    filteredContext = filteredContext,
                    selectedCount = it.size,
                    fallbackRounds = fallbackRounds,
                )
            }
    }

    companion object {
        private val FALLBACK_MULTIPLIERS = listOf(2.0, 3.0, 4.0)
    }

    private fun CandidateScoringSourceContext.filterByCategoryFit(): CandidateScoringSourceContext {
        val requestedCategoryIds = categoryIds.toSet()
        if (requestedCategoryIds.isEmpty() || candidates.isEmpty()) {
            return this
        }

        return copy(
            candidates =
                candidates.filter { candidate ->
                    CategoryFitPolicy.hasCategoryFit(
                        categoryScoresByContentId[candidate.contentId].orEmpty(),
                        requestedCategoryIds,
                    )
                },
        )
    }
}

private class CandidateSelectionTrace {
    private val timings = linkedMapOf<String, Long>()

    fun <T> measure(
        operation: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            timings[operation] = elapsedMillisSince(startedAt)
        }
    }

    fun <T> measureAccumulated(
        operation: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            timings[operation] = (timings[operation] ?: 0L) + elapsedMillisSince(startedAt)
        }
    }

    fun logIfSlow(
        request: RecommendationCandidateSelectionRequest,
        context: CandidateScoringSourceContext,
        filteredContext: CandidateScoringSourceContext,
        selectedCount: Int,
        fallbackRounds: Int,
    ) {
        val totalMillis = timings.values.sum()
        if (totalMillis < SLOW_SELECTION_LOG_THRESHOLD_MS) {
            return
        }

        recommendationCandidateSelectorLogger.info(
            "recommendation_select_completed categoryIds={} candidateCount={} filteredCandidateCount={} selectedCount={} fallbackRounds={} timingsMs={}",
            request.categoryIds,
            context.candidates.size,
            filteredContext.candidates.size,
            selectedCount,
            fallbackRounds,
            timings,
        )
    }

    private fun elapsedMillisSince(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}

data class RecommendationCandidateSelectionRequest(
    val candidates: List<ExposureContentRecommendationCandidateRow>,
    val candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
    val keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    val categoryIds: List<Long>,
    val limit: Int,
)
