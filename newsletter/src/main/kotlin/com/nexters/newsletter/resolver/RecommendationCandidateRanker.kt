package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.springframework.stereotype.Component

@Component
class RecommendationCandidateRanker {
    private val calculator = RecommendScoreCalculator()

    fun rank(
        sourcesByCandidate: Map<ExposureContentRecommendationCandidateRow, RecommendCalculateSource>,
    ): List<ScoredRecommendationCandidate> =
        sourcesByCandidate
            .map { (candidate, source) -> score(candidate, source) }
            .sortedWith(SCORED_CANDIDATE_COMPARATOR)

    fun score(
        candidate: ExposureContentRecommendationCandidateRow,
        source: RecommendCalculateSource,
    ): ScoredRecommendationCandidate =
        ScoredRecommendationCandidate(
            candidate = candidate,
            recommendScore = calculator.calculateScore(source),
        )

    companion object {
        val SCORED_CANDIDATE_COMPARATOR: Comparator<ScoredRecommendationCandidate> =
            compareByDescending<ScoredRecommendationCandidate> { it.recommendScore }
                .thenByDescending { it.candidate.publishedAt }
                .thenByDescending { it.candidate.exposureContentId }
    }
}

data class ScoredRecommendationCandidate(
    val candidate: ExposureContentRecommendationCandidateRow,
    val recommendScore: Double,
)
