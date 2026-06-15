package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
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

    @Test
    fun `select should filter candidates dominated by another category`() {
        val backendCandidate = candidate(exposureContentId = 1L, contentProviderId = 100L)
        val frontendCandidate = candidate(exposureContentId = 2L, contentProviderId = 200L)
        val backendKeyword = ReservedKeyword(id = 10L, name = "API")
        val frontendKeyword = ReservedKeyword(id = 20L, name = "React")
        val context =
            context(listOf(backendCandidate, frontendCandidate)).copy(
                categoryIds = listOf(1L),
                keywordsByContentId =
                    mapOf(
                        backendCandidate.contentId to listOf(backendKeyword),
                        frontendCandidate.contentId to listOf(backendKeyword, frontendKeyword),
                    ),
                keywordCategoryWeightsByKeywordId =
                    mapOf(
                        10L to listOf(CategoryWeight(categoryId = 1L, weight = 4.0)),
                        20L to listOf(CategoryWeight(categoryId = 2L, weight = 4.0)),
                    ),
                contentProviderCategoryWeightsByProviderId =
                    mapOf(
                        100L to listOf(CategoryWeight(categoryId = 1L, weight = 25.0)),
                        200L to listOf(CategoryWeight(categoryId = 2L, weight = 25.0)),
                    ),
            )
        val filteredSources = sourcesByCandidate("filtered", backendCandidate)

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context
        every {
            scoringSourceFactory.createSources(
                match { it.candidates == listOf(backendCandidate) },
                1.0,
            )
        } returns filteredSources
        every { ranker.rank(filteredSources) } returns listOf(ScoredRecommendationCandidate(backendCandidate, 10.0))
        every {
            publisherDiversityPolicy.apply(
                candidates = listOf(backendCandidate),
                sourcesByCandidate = filteredSources,
                limit = 1,
            )
        } returns listOf(backendCandidate)

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = listOf(backendCandidate, frontendCandidate),
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(1L),
                    limit = 1,
                ),
            )

        assertThat(result).containsExactly(backendCandidate)
        verify(exactly = 1) {
            scoringSourceFactory.createSources(
                match { it.candidates == listOf(backendCandidate) },
                1.0,
            )
        }
    }

    @Test
    fun `select should filter candidates whose provider has no requested category mapping`() {
        val candidate = candidate(exposureContentId = 1L, contentProviderId = 100L)
        val backendKeyword = ReservedKeyword(id = 10L, name = "Android")
        val frontendKeyword = ReservedKeyword(id = 20L, name = "Frontend")
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(4L),
                keywordsByContentId = mapOf(candidate.contentId to listOf(backendKeyword, frontendKeyword)),
                keywordCategoryWeightsByKeywordId =
                    mapOf(
                        10L to listOf(CategoryWeight(categoryId = 4L, weight = 29.0)),
                        20L to listOf(CategoryWeight(categoryId = 2L, weight = 8.0)),
                    ),
                contentProviderCategoryWeightsByProviderId =
                    mapOf(
                        100L to listOf(CategoryWeight(categoryId = 2L, weight = 25.0)),
                    ),
            )

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = listOf(candidate),
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(4L),
                    limit = 1,
                ),
            )

        assertThat(result).isEmpty()
        verify(exactly = 0) { scoringSourceFactory.createSources(any(), any()) }
    }

    @Test
    fun `select should reject single category candidates when another category is stronger within tolerance`() {
        val candidate = candidate(exposureContentId = 1L, contentProviderId = 100L)
        val sharedKeyword = ReservedKeyword(id = 10L, name = "Kotlin")
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(1L),
                keywordsByContentId = mapOf(candidate.contentId to listOf(sharedKeyword)),
                keywordCategoryWeightsByKeywordId =
                    mapOf(
                        10L to
                            listOf(
                                CategoryWeight(categoryId = 1L, weight = 17.0),
                                CategoryWeight(categoryId = 4L, weight = 20.0),
                            ),
                    ),
                contentProviderCategoryWeightsByProviderId =
                    mapOf(
                        100L to
                            listOf(
                                CategoryWeight(categoryId = 1L, weight = 14.0),
                                CategoryWeight(categoryId = 4L, weight = 18.0),
                            ),
                    ),
            )

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = listOf(candidate),
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(1L),
                    limit = 1,
                ),
            )

        assertThat(result).isEmpty()
        verify(exactly = 0) { scoringSourceFactory.createSources(any(), any()) }
    }

    @Test
    fun `select should reject single category candidates when requested category only barely beats another category`() {
        val candidate = candidate(exposureContentId = 1L, contentProviderId = 100L)
        val backendKeyword = ReservedKeyword(id = 10L, name = "API")
        val sharedKeyword = ReservedKeyword(id = 20L, name = "Kotlin")
        val androidKeyword = ReservedKeyword(id = 30L, name = "Android")
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(1L),
                keywordsByContentId = mapOf(candidate.contentId to listOf(backendKeyword, sharedKeyword, androidKeyword)),
                keywordCategoryWeightsByKeywordId =
                    mapOf(
                        10L to listOf(CategoryWeight(categoryId = 1L, weight = 4.0)),
                        20L to
                            listOf(
                                CategoryWeight(categoryId = 1L, weight = 4.0),
                                CategoryWeight(categoryId = 4L, weight = 4.0),
                            ),
                        30L to listOf(CategoryWeight(categoryId = 4L, weight = 3.0)),
                    ),
                contentProviderCategoryWeightsByProviderId =
                    mapOf(
                        100L to
                            listOf(
                                CategoryWeight(categoryId = 1L, weight = 25.0),
                                CategoryWeight(categoryId = 4L, weight = 25.0),
                            ),
                    ),
            )

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = listOf(candidate),
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(1L),
                    limit = 1,
                ),
            )

        assertThat(result).isEmpty()
        verify(exactly = 0) { scoringSourceFactory.createSources(any(), any()) }
    }

    @Test
    fun `select should cap provider category fit weights so article keywords decide fit`() {
        val candidate = candidate(exposureContentId = 1L, contentProviderId = 100L)
        val backendKeyword = ReservedKeyword(id = 10L, name = "API")
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(1L),
                keywordsByContentId = mapOf(candidate.contentId to listOf(backendKeyword)),
                keywordCategoryWeightsByKeywordId =
                    mapOf(
                        10L to listOf(CategoryWeight(categoryId = 1L, weight = 4.0)),
                    ),
                contentProviderCategoryWeightsByProviderId =
                    mapOf(
                        100L to
                            listOf(
                                CategoryWeight(categoryId = 1L, weight = 25.0),
                                CategoryWeight(categoryId = 4L, weight = 100.0),
                            ),
                    ),
            )
        val filteredSources = sourcesByCandidate("filtered", candidate)

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context
        every {
            scoringSourceFactory.createSources(
                match { it.candidates == listOf(candidate) },
                1.0,
            )
        } returns filteredSources
        every { ranker.rank(filteredSources) } returns listOf(ScoredRecommendationCandidate(candidate, 10.0))
        every {
            publisherDiversityPolicy.apply(
                candidates = listOf(candidate),
                sourcesByCandidate = filteredSources,
                limit = 1,
            )
        } returns listOf(candidate)

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = listOf(candidate),
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(1L),
                    limit = 1,
                ),
            )

        assertThat(result).containsExactly(candidate)
    }

    private fun context(candidates: List<ExposureContentRecommendationCandidateRow>): CandidateScoringSourceContext =
        CandidateScoringSourceContext(
            candidates = candidates,
            candidateSignalsByExposureContentId = emptyMap(),
            keywordWeightsByKeywordId = emptyMap(),
            keywordsByContentId = emptyMap(),
            categoryIds = listOf(10L),
            categoryMatchWeights = emptyMap(),
            keywordCategoryWeightsByKeywordId = emptyMap(),
            contentProviderCategoryWeightsByProviderId = emptyMap(),
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

    private fun candidate(
        exposureContentId: Long,
        contentProviderId: Long = exposureContentId * 100,
    ): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = exposureContentId,
            contentId = exposureContentId * 10,
            contentProviderId = contentProviderId,
            contentProviderName = "Provider $exposureContentId",
            newsletterName = "Newsletter $exposureContentId",
            publishedAt = LocalDate.of(2026, 6, 9),
            title = "Title $exposureContentId",
            provocativeHeadline = "Headline $exposureContentId",
            summaryContent = "Summary $exposureContentId",
        )
}
