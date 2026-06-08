package com.nexters.newsletter.resolver

import com.nexters.external.entity.Category
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.User
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.DailyContentArchiveService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyContentArchiveResolverTest {
    private val userService = mockk<UserService>()
    private val categoryService = mockk<CategoryService>()
    private val dailyContentArchiveService = mockk<DailyContentArchiveService>()
    private val possibleContentsResolver = mockk<PossibleContentsResolver>()
    private val recommendationCandidateSelector = mockk<RecommendationCandidateSelector>()
    private val exposureContentService = mockk<ExposureContentService>()

    private val resolver =
        DailyContentArchiveResolver(
            userService = userService,
            categoryService = categoryService,
            dailyContentArchiveService = dailyContentArchiveService,
            possibleContentsResolver = possibleContentsResolver,
            recommendationCandidateSelector = recommendationCandidateSelector,
            exposureContentService = exposureContentService,
        )

    @Test
    fun `resolveTodayContentArchive should delegate candidates to selector even when candidate count is less than max content size`() {
        val userId = 1L
        val categoryId = 10L
        val keyword = ReservedKeyword(id = 1L, name = "AI")
        val category = Category(id = categoryId, name = "BE")
        val user =
            User(
                id = userId,
                deviceToken = "test-device-token",
                categories = mutableSetOf(category),
            )
        val candidate = candidate(exposureContentId = 1L, contentId = 100L)
        val exposureContent = mockk<ExposureContent>()
        val date = LocalDate.of(2026, 6, 9)

        every { dailyContentArchiveService.findByDateAndUserId(userId, date) } returns null
        every { userService.getUserById(userId) } returns user
        every {
            possibleContentsResolver.resolveCandidatePoolByCategoryIds(
                userId = userId,
                categoryIds = listOf(categoryId),
            )
        } returns
            CandidatePool(
                candidates =
                    listOf(
                        CandidatePoolItem(
                            candidate = candidate,
                            signals =
                                listOf(
                                    CandidateSourceSignal(
                                        source = "test_source",
                                        score = 1.0,
                                        confidence = 0.8,
                                        reason = "test",
                                    ),
                                ),
                        ),
                    ),
                expandedWindow = CandidateRecencyWindow.DAYS_30,
            )
        every { categoryService.getKeywordWeightsByCategoryIds(listOf(categoryId)) } returns mapOf(keyword to 2.0)
        every {
            recommendationCandidateSelector.select(any())
        } returns listOf(candidate)
        every { exposureContentService.getExposureContentsByIdsPreservingOrder(listOf(candidate.exposureContentId)) } returns
            listOf(exposureContent)
        every { dailyContentArchiveService.saveWithHistory(any()) } answers { firstArg<DailyContentArchive>() }

        val result = resolver.resolveTodayContentArchive(userId, date)

        assertThat(result.exposureContents).containsExactly(exposureContent)
        verify {
            recommendationCandidateSelector.select(
                match { request ->
                    request.candidates == listOf(candidate) &&
                        request.keywordWeightsByKeyword == mapOf(keyword to 2.0) &&
                        request.categoryIds == listOf(categoryId) &&
                        request.limit == 6
                },
            )
        }
        verify { exposureContentService.getExposureContentsByIdsPreservingOrder(listOf(candidate.exposureContentId)) }
    }

    private fun candidate(
        exposureContentId: Long,
        contentId: Long,
    ): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = exposureContentId,
            contentId = contentId,
            contentProviderId = 200L,
            contentProviderName = "Provider",
            newsletterName = "Newsletter",
            publishedAt = LocalDate.now(),
            title = "New AI platform release",
            provocativeHeadline = "New AI platform release",
            summaryContent = "A new AI platform was released.",
        )
}
