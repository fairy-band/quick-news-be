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
                    val publisherDuplicateCandidateCount = candidatePublisherCounts.getOrDefault(publisherId, 0)
                    val adjustedSource =
                        source.copy(
                            publisherDuplicateCandidateCount = publisherDuplicateCandidateCount,
                        )

                    candidatePublisherCounts[publisherId] = publisherDuplicateCandidateCount + 1

                    ranker.score(candidate, adjustedSource)
                }.sortedWith(RecommendationCandidateRanker.SCORED_CANDIDATE_COMPARATOR)
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
}

internal val ExposureContentRecommendationCandidateRow.publisherName: String
    get() = contentProviderName ?: newsletterName
