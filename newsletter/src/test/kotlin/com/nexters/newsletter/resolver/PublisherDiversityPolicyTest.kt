package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PublisherDiversityPolicyTest {
    private val policy = PublisherDiversityPolicy(RecommendationCandidateRanker())

    @Test
    fun `apply should limit repeated publishers while preserving enough candidates`() {
        val candidates =
            listOf(
                candidate(exposureContentId = 1L, publisher = "Same"),
                candidate(exposureContentId = 2L, publisher = "Same"),
                candidate(exposureContentId = 3L, publisher = "Same"),
                candidate(exposureContentId = 4L, publisher = "Other"),
            )
        val sourcesByCandidate =
            candidates.associateWith {
                RecommendCalculateSource(
                    positiveKeywordSources = listOf(PositiveKeywordSource(weight = 100.0)),
                    negativeKeywordSources = emptyList(),
                    publishedDate = it.publishedAt,
                    publisherDuplicateCandidateCount = 0,
                )
            }

        val result =
            policy.apply(
                candidates = candidates,
                sourcesByCandidate = sourcesByCandidate,
                limit = 3,
            )

        assertThat(result).hasSize(3)
        assertThat(result.count { it.publisherName == "Same" }).isLessThanOrEqualTo(2)
        assertThat(result.map { it.publisherName }).contains("Other")
    }

    private fun candidate(
        exposureContentId: Long,
        publisher: String,
    ): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = exposureContentId,
            contentId = exposureContentId * 10,
            contentProviderId = exposureContentId * 100,
            contentProviderName = publisher,
            newsletterName = publisher,
            publishedAt = LocalDate.now(),
            title = "Title $exposureContentId",
            provocativeHeadline = "Headline $exposureContentId",
            summaryContent = "Summary $exposureContentId",
        )
}
