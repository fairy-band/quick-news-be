package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.springframework.stereotype.Component

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

        val context =
            scoringSourceFactory.createContext(
                candidates = request.candidates,
                candidateSignalsByExposureContentId = request.candidateSignalsByExposureContentId,
                keywordWeightsByKeyword = request.keywordWeightsByKeyword,
                categoryIds = request.categoryIds,
            )
        val sourcesByCandidate = scoringSourceFactory.createSources(context, multiplier = 1.0)
        val scoredCandidates = ranker.rank(sourcesByCandidate)
        val positiveScoreCandidates =
            scoredCandidates
                .filter { it.recommendScore > 0 }
                .map { it.candidate }

        if (positiveScoreCandidates.size >= request.limit) {
            return publisherDiversityPolicy.apply(
                candidates = positiveScoreCandidates,
                sourcesByCandidate = sourcesByCandidate,
                limit = request.limit,
            )
        }

        val selectedCandidates = LinkedHashSet<ExposureContentRecommendationCandidateRow>(positiveScoreCandidates)

        for (multiplier in FALLBACK_MULTIPLIERS) {
            if (selectedCandidates.size >= request.limit) {
                break
            }

            val amplifiedSourcesByCandidate = scoringSourceFactory.createSources(context, multiplier)
            val amplifiedCandidates =
                ranker
                    .rank(amplifiedSourcesByCandidate)
                    .filter { it.candidate !in selectedCandidates }
                    .filter { it.recommendScore > 0 }
                    .map { it.candidate }
            val additionalNeeded = request.limit - selectedCandidates.size
            selectedCandidates.addAll(amplifiedCandidates.take(additionalNeeded))
        }

        return selectedCandidates.take(request.limit)
    }

    companion object {
        private val FALLBACK_MULTIPLIERS = listOf(2.0, 3.0, 4.0)
    }
}

data class RecommendationCandidateSelectionRequest(
    val candidates: List<ExposureContentRecommendationCandidateRow>,
    val candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
    val keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    val categoryIds: List<Long>,
    val limit: Int,
)
