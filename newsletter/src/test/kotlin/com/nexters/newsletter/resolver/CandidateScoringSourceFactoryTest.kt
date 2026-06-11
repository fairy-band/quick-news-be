package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentKeywordProjection
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentProviderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CandidateScoringSourceFactoryTest {
    private val contentKeywordMappingRepository = mockk<ContentKeywordMappingRepository>()
    private val contentProviderService = mockk<ContentProviderService>()
    private val categoryService = mockk<CategoryService>()
    private val factory =
        CandidateScoringSourceFactory(
            contentKeywordMappingRepository = contentKeywordMappingRepository,
            contentProviderService = contentProviderService,
            categoryService = categoryService,
        )

    @Test
    fun `createSources should build scoring sources from id based keyword and category features`() {
        val candidate = candidate(exposureContentId = 1L, contentId = 100L, contentProviderId = 200L)
        val positiveKeyword = ReservedKeyword(id = 1L, name = "AI")
        val negativeKeyword = ReservedKeyword(id = 2L, name = "Legacy")
        val contentPositiveKeyword = ReservedKeyword(id = 1L, name = "AI")
        val contentNegativeKeyword = ReservedKeyword(id = 2L, name = "Legacy")
        val signals =
            listOf(
                CandidateSourceSignal(
                    source = "category_keyword",
                    score = 1.0,
                    confidence = 0.75,
                    reason = "test",
                ),
            )

        every { contentKeywordMappingRepository.findKeywordsByContentIds(listOf(candidate.contentId)) } returns
            listOf(
                contentKeywordProjection(candidate.contentId, contentPositiveKeyword),
                contentKeywordProjection(candidate.contentId, contentNegativeKeyword),
            )
        every { contentProviderService.getAllCategoryMatchWeights(listOf(200L)) } returns
            mapOf(
                (200L to 10L) to 7.0,
                (200L to 11L) to 3.0,
            )
        every { categoryService.getKeywordCategoryWeightsByKeywordIds(listOf(1L, 2L)) } returns
            mapOf(
                (1L to 10L) to 2.0,
                (2L to 11L) to 4.0,
            )

        val context =
            factory.createContext(
                candidates = listOf(candidate),
                candidateSignalsByExposureContentId = mapOf(candidate.exposureContentId to signals),
                keywordWeightsByKeyword = mapOf(positiveKeyword to 2.0, negativeKeyword to -3.0),
                categoryIds = listOf(10L),
            )
        val source = factory.createSources(context, multiplier = 2.0).getValue(candidate)

        assertThat(source.keywordNames).containsExactly("AI", "Legacy")
        assertThat(source.candidateSignals).containsExactlyElementsOf(signals)
        assertThat(source.positiveKeywordSources).containsExactly(PositiveKeywordSource(weight = 4.0, keywordName = "AI"))
        assertThat(source.negativeKeywordSources).containsExactly(NegativeKeywordSource(weight = -3.0, keywordName = "Legacy"))
        assertThat(source.categoryMatchBonus).isEqualTo(7.0)
        assertThat(context.keywordCategoryWeightsByKeywordId)
            .containsEntry(1L, listOf(CategoryWeight(categoryId = 10L, weight = 2.0)))
        assertThat(context.contentProviderCategoryWeightsByProviderId)
            .containsEntry(
                200L,
                listOf(
                    CategoryWeight(categoryId = 10L, weight = 7.0),
                    CategoryWeight(categoryId = 11L, weight = 3.0),
                ),
            )
        verify(exactly = 1) { contentKeywordMappingRepository.findKeywordsByContentIds(listOf(candidate.contentId)) }
        verify(exactly = 1) { contentProviderService.getAllCategoryMatchWeights(listOf(200L)) }
        verify(exactly = 1) { categoryService.getKeywordCategoryWeightsByKeywordIds(listOf(1L, 2L)) }
    }

    private fun contentKeywordProjection(
        contentId: Long,
        keyword: ReservedKeyword,
    ): ContentKeywordProjection =
        object : ContentKeywordProjection {
            override val contentId: Long = contentId
            override val keyword: ReservedKeyword = keyword
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
