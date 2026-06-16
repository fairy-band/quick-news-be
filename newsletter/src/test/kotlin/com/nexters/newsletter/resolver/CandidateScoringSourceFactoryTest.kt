package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentKeywordFeatureProjection
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.category.ContentCategoryScoreService
import com.nexters.external.service.category.ContentCategoryScoreSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CandidateScoringSourceFactoryTest {
    private val contentKeywordMappingRepository = mockk<ContentKeywordMappingRepository>()
    private val contentCategoryScoreService = mockk<ContentCategoryScoreService>()
    private val factory =
        CandidateScoringSourceFactory(
            contentKeywordMappingRepository = contentKeywordMappingRepository,
            contentCategoryScoreService = contentCategoryScoreService,
        )

    @Test
    fun `createSources should build scoring sources from id based keyword and category features`() {
        val candidate = candidate(exposureContentId = 1L, contentId = 100L, contentProviderId = 200L)
        val positiveKeyword = ReservedKeyword(id = 1L, name = "AI")
        val negativeKeyword = ReservedKeyword(id = 2L, name = "Legacy")
        val signals =
            listOf(
                CandidateSourceSignal(
                    source = "category_keyword",
                    score = 1.0,
                    confidence = 0.75,
                    reason = "test",
                ),
            )

        every { contentKeywordMappingRepository.findKeywordFeaturesByContentIds(listOf(candidate.contentId)) } returns
            listOf(
                contentKeywordProjection(candidate.contentId, keywordId = 1L, keywordName = "AI"),
                contentKeywordProjection(candidate.contentId, keywordId = 2L, keywordName = "Legacy"),
            )
        every { contentCategoryScoreService.getScoresByContentIds(listOf(candidate.contentId)) } returns
            mapOf(
                candidate.contentId to
                    listOf(
                        ContentCategoryScoreSnapshot(
                            contentId = candidate.contentId,
                            categoryId = 10L,
                            keywordScore = 2.0,
                            providerScore = 7.0,
                            totalScore = 9.0,
                        ),
                    ),
            )

        val context =
            factory.createContext(
                candidates = listOf(candidate),
                candidateSignalsByExposureContentId = mapOf(candidate.exposureContentId to signals),
                keywordWeightsByKeyword = mapOf(positiveKeyword to 2.0, negativeKeyword to -3.0),
                categoryIds = listOf(10L),
            )
        val scoringContext = factory.loadScoringFeatures(context)
        val source = factory.createSources(scoringContext, multiplier = 2.0).getValue(candidate)

        assertThat(source.keywordNames).containsExactly("AI", "Legacy")
        assertThat(source.candidateSignals).containsExactlyElementsOf(signals)
        assertThat(source.positiveKeywordSources).containsExactly(PositiveKeywordSource(weight = 4.0, keywordName = "AI"))
        assertThat(source.negativeKeywordSources).containsExactly(NegativeKeywordSource(weight = -3.0, keywordName = "Legacy"))
        assertThat(source.categoryMatchBonus).isEqualTo(7.0)
        assertThat(context.categoryScoresByContentId)
            .containsEntry(
                candidate.contentId,
                listOf(
                    ContentCategoryScoreSnapshot(
                        contentId = candidate.contentId,
                        categoryId = 10L,
                        keywordScore = 2.0,
                        providerScore = 7.0,
                        totalScore = 9.0,
                    ),
                ),
            )
        verify(exactly = 1) { contentKeywordMappingRepository.findKeywordFeaturesByContentIds(listOf(candidate.contentId)) }
        verify(exactly = 1) { contentCategoryScoreService.getScoresByContentIds(listOf(candidate.contentId)) }
    }

    private fun contentKeywordProjection(
        contentId: Long,
        keywordId: Long,
        keywordName: String,
    ): ContentKeywordFeatureProjection =
        object : ContentKeywordFeatureProjection {
            override val contentId: Long = contentId
            override val keywordId: Long = keywordId
            override val keywordName: String = keywordName
        }

    private fun candidate(
        exposureContentId: Long,
        contentId: Long,
        contentProviderId: Long?,
    ): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = exposureContentId,
            contentId = contentId,
            contentProviderId = contentProviderId,
            contentProviderName = "Provider",
            newsletterName = "Newsletter",
            publishedAt = LocalDate.of(2026, 6, 9),
            title = "Title",
            provocativeHeadline = "Headline",
            summaryContent = "Summary",
        )
}
