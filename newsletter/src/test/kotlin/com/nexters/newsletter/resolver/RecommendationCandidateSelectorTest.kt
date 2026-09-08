package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.category.ContentCategoryScoreSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun setUp() {
        every { scoringSourceFactory.loadScoringFeatures(any()) } answers { firstArg() }
    }

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
        verify(exactly = 1) { scoringSourceFactory.loadScoringFeatures(context) }
        verify(exactly = 1) { scoringSourceFactory.createSources(context, 1.0) }
        verify(exactly = 1) { scoringSourceFactory.createSources(context, 2.0) }
    }

    @Test
    fun `select should filter candidates dominated by another category`() {
        val backendCandidate = candidate(exposureContentId = 1L, contentProviderId = 100L)
        val frontendCandidate = candidate(exposureContentId = 2L, contentProviderId = 200L)
        val context =
            context(listOf(backendCandidate, frontendCandidate)).copy(
                categoryIds = listOf(1L),
                categoryScoresByContentId =
                    mapOf(
                        backendCandidate.contentId to
                            listOf(categoryScore(backendCandidate.contentId, categoryId = 1L, keywordScore = 4.0, providerScore = 4.0)),
                        frontendCandidate.contentId to
                            listOf(
                                categoryScore(frontendCandidate.contentId, categoryId = 1L, keywordScore = 4.0),
                                categoryScore(frontendCandidate.contentId, categoryId = 2L, keywordScore = 4.0, providerScore = 4.0),
                            ),
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
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(4L),
                categoryScoresByContentId =
                    mapOf(
                        candidate.contentId to
                            listOf(
                                categoryScore(candidate.contentId, categoryId = 4L, keywordScore = 29.0),
                                categoryScore(candidate.contentId, categoryId = 2L, keywordScore = 8.0, providerScore = 4.0),
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
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(1L),
                categoryScoresByContentId =
                    mapOf(
                        candidate.contentId to
                            listOf(
                                categoryScore(candidate.contentId, categoryId = 1L, keywordScore = 17.0, providerScore = 4.0),
                                categoryScore(candidate.contentId, categoryId = 4L, keywordScore = 20.0, providerScore = 4.0),
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
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(1L),
                categoryScoresByContentId =
                    mapOf(
                        candidate.contentId to
                            listOf(
                                categoryScore(candidate.contentId, categoryId = 1L, keywordScore = 8.0, providerScore = 4.0),
                                categoryScore(candidate.contentId, categoryId = 4L, keywordScore = 7.0, providerScore = 4.0),
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
        val context =
            context(listOf(candidate)).copy(
                categoryIds = listOf(1L),
                categoryScoresByContentId =
                    mapOf(
                        candidate.contentId to
                            listOf(
                                categoryScore(candidate.contentId, categoryId = 1L, keywordScore = 4.0, providerScore = 4.0),
                                categoryScore(candidate.contentId, categoryId = 4L, providerScore = 4.0),
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

    @Test
    fun `select should apply topic deduplication so duplicate topic articles are not repeated in daily recommendation`() {
        val topic1A = candidate(exposureContentId = 1L).copy(title = "React 19 정식 출시 안내", provocativeHeadline = "React 19 정식 출시")
        val topic1B = candidate(exposureContentId = 2L).copy(title = "React 19 신규 기능과 마이그레이션", provocativeHeadline = "React 19 신규 기능")
        val topic2 = candidate(exposureContentId = 3L).copy(title = "Spring Boot 3.3 업데이트 가이드", provocativeHeadline = "Spring Boot 3.3 업데이트")
        val topic3 = candidate(exposureContentId = 4L).copy(title = "PostgreSQL 트랜잭션 튜닝 노하우", provocativeHeadline = "PostgreSQL 트랜잭션")
        val topic4 = candidate(exposureContentId = 5L).copy(title = "Kafka 파티션 분산 아키텍처", provocativeHeadline = "Kafka 파티션 분산")
        val topic5 = candidate(exposureContentId = 6L).copy(title = "Docker 보안 베스트 프랙티스", provocativeHeadline = "Docker 보안 가이드")
        val topic6 = candidate(exposureContentId = 7L).copy(title = "Redis 캐시 무효화 전략", provocativeHeadline = "Redis 캐시 전략")

        val allCandidates = listOf(topic1A, topic1B, topic2, topic3, topic4, topic5, topic6)
        val context = context(allCandidates)
        val sources = sourcesByCandidate("sources", *allCandidates.toTypedArray())

        every { scoringSourceFactory.createContext(any(), any(), any(), any()) } returns context
        every { scoringSourceFactory.createSources(match { it.candidates == allCandidates }, 1.0) } returns sources
        every { ranker.rank(sources) } returns allCandidates.mapIndexed { idx, cand ->
            ScoredRecommendationCandidate(cand, 100.0 - idx * 5.0)
        }
        every {
            publisherDiversityPolicy.apply(
                candidates = allCandidates,
                sourcesByCandidate = sources,
                limit = 6,
            )
        } returns listOf(topic1A, topic1B, topic2, topic3, topic4, topic5)

        val result =
            selector.select(
                RecommendationCandidateSelectionRequest(
                    candidates = allCandidates,
                    candidateSignalsByExposureContentId = emptyMap(),
                    keywordWeightsByKeyword = emptyMap(),
                    categoryIds = listOf(10L),
                    limit = 6,
                ),
            )

        assertThat(result).hasSize(6)
        assertThat(result).contains(topic1A)
        assertThat(result).doesNotContain(topic1B)
        assertThat(result).contains(topic2, topic3, topic4, topic5, topic6)
    }

    private fun context(candidates: List<ExposureContentRecommendationCandidateRow>): CandidateScoringSourceContext =
        CandidateScoringSourceContext(
            candidates = candidates,
            candidateSignalsByExposureContentId = emptyMap(),
            keywordWeightsByKeywordId = emptyMap(),
            keywordsByContentId = emptyMap(),
            categoryIds = listOf(10L),
            categoryMatchWeights = emptyMap(),
            categoryScoresByContentId = emptyMap(),
        )

    private fun categoryScore(
        contentId: Long,
        categoryId: Long,
        keywordScore: Double = 0.0,
        providerScore: Double = 0.0,
    ): ContentCategoryScoreSnapshot =
        ContentCategoryScoreSnapshot(
            contentId = contentId,
            categoryId = categoryId,
            keywordScore = keywordScore,
            providerScore = providerScore,
            totalScore = keywordScore + providerScore,
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
