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

        val finalSelected =
            if (positiveScoreCandidates.size >= request.limit) {
                trace.measure("publisherDiversity") {
                    publisherDiversityPolicy.apply(
                        candidates = positiveScoreCandidates,
                        sourcesByCandidate = sourcesByCandidate,
                        limit = request.limit,
                    )
                }
            } else {
                val fallbackSelected = LinkedHashSet<ExposureContentRecommendationCandidateRow>(positiveScoreCandidates)
                for (multiplier in FALLBACK_MULTIPLIERS) {
                    if (fallbackSelected.size >= request.limit) {
                        break
                    }
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
                            .filter { it.candidate !in fallbackSelected }
                            .filter { it.recommendScore > 0 }
                            .map { it.candidate }
                    val additionalNeeded = request.limit - fallbackSelected.size
                    fallbackSelected.addAll(amplifiedCandidates.take(additionalNeeded))
                }
                fallbackSelected.take(request.limit)
            }

        // Extract semantic vector candidates from candidate signals
        val semanticCandidates =
            request.candidateSignalsByExposureContentId.entries
                .filter { (_, signals) -> signals.any { it.source == "semantic_vector" } }
                .mapNotNull { (exposureContentId, _) ->
                    filteredContext.candidates.find { it.exposureContentId == exposureContentId }
                }

        // Apply 6-slot Hybrid Interleaving (Slot 1: Core, Slot 2: Semantic#1, Slot 3: Trend, Slot 4: MAB, Slot 5: Semantic#2, Slot 6: Evergreen)
        val result =
            interleaveHybridRecommendationSlots(
                rankedCandidates = finalSelected,
                semanticCandidates = semanticCandidates,
                allCandidates = filteredContext.candidates,
                limit = request.limit,
            )

        trace.logIfSlow(
            request = request,
            context = context,
            filteredContext = filteredContext,
            selectedCount = result.size,
            fallbackRounds = 0,
        )
        return result
    }

    private fun interleaveHybridRecommendationSlots(
        rankedCandidates: List<ExposureContentRecommendationCandidateRow>,
        semanticCandidates: List<ExposureContentRecommendationCandidateRow>,
        allCandidates: List<ExposureContentRecommendationCandidateRow>,
        limit: Int,
    ): List<ExposureContentRecommendationCandidateRow> {
        if (rankedCandidates.isEmpty() || limit <= 0) {
            return emptyList()
        }

        if (limit < 3) {
            return rankedCandidates.take(limit)
        }

        val result = LinkedHashSet<ExposureContentRecommendationCandidateRow>()
        val today = java.time.LocalDate.now()

        // [Slot 1 / Index 0] Core 1st rank anchor
        val slot1 = rankedCandidates.firstOrNull()
        if (slot1 != null) {
            result.add(slot1)
        }

        // [Slot 2 / Index 1] Semantic Candidate #1 (Highest similarity)
        val slot2 =
            semanticCandidates.firstOrNull { it !in result }
                ?: rankedCandidates.firstOrNull { it !in result }
        if (slot2 != null) {
            result.add(slot2)
        }

        // [Slot 3 / Index 2] Trend & Hot News / Next Highest Ranked
        val slot3 = rankedCandidates.firstOrNull { it !in result }
        if (slot3 != null) {
            result.add(slot3)
        }

        // [Slot 4 / Index 3] MAB Exploration Slot (Fresh content published within last 2 days)
        val mabCandidate =
            allCandidates
                .filter { it !in result }
                .filter { java.time.temporal.ChronoUnit.DAYS.between(it.publishedAt, today) <= 2 }
                .maxByOrNull { it.publishedAt }

        if (mabCandidate != null) {
            result.add(mabCandidate)
        } else {
            rankedCandidates.firstOrNull { it !in result }?.let { result.add(it) }
        }

        // [Slot 5 / Index 4] Semantic Candidate #2 (With publisher diversity)
        val slot5 =
            semanticCandidates.firstOrNull { candidate ->
                candidate !in result && result.none { it.contentProviderId == candidate.contentProviderId && candidate.contentProviderId != null }
            } ?: semanticCandidates.firstOrNull { it !in result }
                ?: rankedCandidates.firstOrNull { it !in result }

        if (slot5 != null) {
            result.add(slot5)
        }

        // [Slot 6 / Index 5+] Evergreen Architecture / Fill remaining slots
        while (result.size < limit) {
            val nextRanked =
                rankedCandidates.firstOrNull { it !in result }
                    ?: allCandidates.firstOrNull { it !in result }
                    ?: break
            result.add(nextRanked)
        }

        return result.take(limit)
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
