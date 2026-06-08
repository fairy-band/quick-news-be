package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecommendationCandidateSelectorTest {
    private val scoringSourceFactory = mockk<CandidateScoringSourceFactory>()
    private val ranker = mockk<RecommendationCandidateRanker>()
    private val publisherDiversityPolicy = mockk<PublisherDiversityPolicy>()
    private val selector =
        RecommendationCandidateSelector(
            scoringSourceFactory = scoringSourceFactory,
            ranker = ranker,
            publisherDiversityPolicy = publisherDiversityPolicy,
        )

    @Test
    fun `select should reuse source context while applying fallback multipliers`() {
        val first = candidate(exposureContentId = 1L)
        val second = candidate(exposureContentId = 2L)
        val context = context(listOf(first, second))
        val defaultSources = sourcesByCandidate("default", first, second)
        val amplifiedSources = sourcesByCandidate("amplified", first, second)

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context
        every { scoringSourceFactory.createSources(context, 1.0) } returns defaultSources
        every { scoringSourceFactory.createSources(context, 2.0) } returns amplifiedSources
        every { ranker.rank(defaultSources) } returns listOf(ScoredRecommendationCandidate(first, 10.0))
        every { ranker.rank(amplifiedSources) } returns
            listOf(
                ScoredRecommendationCandidate(first, 20.0),
                ScoredRecommendationCandidate(second, 5.0),
            )

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = listOf(first, second),
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(10L),
                    limit = 2,
                ),
            )

        assertThat(result).containsExactly(first, second)
        verify(exactly = 1) { scoringSourceFactory.createContext(any(), any(), any(), any()) }
        verify(exactly = 1) { scoringSourceFactory.createSources(context, 1.0) }
        verify(exactly = 1) { scoringSourceFactory.createSources(context, 2.0) }
    }

    private fun context(candidates: List<ExposureContentRecommendationCandidateRow>): CandidateScoringSourceContext =
        CandidateScoringSourceContext(
            candidates = candidates,
            candidateSignalsByExposureContentId = emptyMap(),
            keywordWeightsByKeyword = emptyMap(),
            keywordsByContentId = emptyMap(),
            categoryIds = listOf(10L),
            categoryMatchWeights = emptyMap(),
        )

    private fun sourcesByCandidate(
        title: String,
        vararg candidates: ExposureContentRecommendationCandidateRow,
    ): Map<ExposureContentRecommendationCandidateRow, RecommendCalculateSource> =
        candidates.associateWith {
            RecommendCalculateSource(
                title = title,
                positiveKeywordSources = emptyList(),
                negativeKeywordSources = emptyList(),
                publishedDate = it.publishedAt,
                publisherDuplicateCandidateCount = 0,
            )
        }

    private fun candidate(exposureContentId: Long): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = exposureContentId,
            contentId = exposureContentId * 10,
            contentProviderId = exposureContentId * 100,
            contentProviderName = "Provider $exposureContentId",
            newsletterName = "Newsletter $exposureContentId",
            publishedAt = LocalDate.of(2026, 6, 9),
            title = "Title $exposureContentId",
            provocativeHeadline = "Headline $exposureContentId",
            summaryContent = "Summary $exposureContentId",
        )
}
