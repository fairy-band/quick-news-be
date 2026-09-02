package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.springframework.stereotype.Component

@Component
class PublisherDiversityPolicy(
    private val ranker: RecommendationCandidateRanker,
) {
    fun apply(
        candidates: List<ExposureContentRecommendationCandidateRow>,
        sourcesByCandidate: Map<ExposureContentRecommendationCandidateRow, RecommendCalculateSource>,
        limit: Int,
    ): List<ExposureContentRecommendationCandidateRow> {
        if (limit <= 0) {
            return emptyList()
        }

        val candidatePublisherCounts = mutableMapOf<String, Int>()
        val maxPerPublisher = (limit - 1).coerceAtLeast(1)

        val scoredCandidates =
            candidates
                .take(limit * limit)
                .map { candidate ->
                    val source = sourcesByCandidate.getValue(candidate)
                    val publisherId = candidate.publisherName
                    val duplicateCount = candidatePublisherCounts.getOrDefault(publisherId, 0)
                    val dampingMultiplier = Math.pow(DAMPING_FACTOR, duplicateCount.toDouble())

                    val scored = ranker.score(candidate, source)
                    val dampedScore = scored.recommendScore * dampingMultiplier

                    candidatePublisherCounts[publisherId] = duplicateCount + 1

                    ScoredCandidateWithDamping(candidate, dampedScore)
                }.sortedByDescending { it.dampedScore }
                .map { it.candidate }

        val selectedPublisherCounts = mutableMapOf<String, Int>()
        val selected = mutableListOf<ExposureContentRecommendationCandidateRow>()
        val rejected = mutableListOf<ExposureContentRecommendationCandidateRow>()

        for (candidate in scoredCandidates) {
            val publisherId = candidate.publisherName
            val count = selectedPublisherCounts.getOrDefault(publisherId, 0)
            if (count < maxPerPublisher) {
                selectedPublisherCounts[publisherId] = count + 1
                selected.add(candidate)
            } else {
                rejected.add(candidate)
            }

            if (selected.size == limit) {
                break
            }
        }

        if (selected.size < limit) {
            selected.addAll(rejected.take(limit - selected.size))
        }

        return selected
    }

    companion object {
        private const val DAMPING_FACTOR = 0.75
    }

    private data class ScoredCandidateWithDamping(
        val candidate: ExposureContentRecommendationCandidateRow,
        val dampedScore: Double,
    )
}

internal val ExposureContentRecommendationCandidateRow.publisherName: String
    get() = contentProviderName ?: newsletterName
