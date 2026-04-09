package com.nexters.admin.controller

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.Summary
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.service.ContentAnalysisService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.GeminiRateLimiterService
import com.nexters.newsletter.service.NewsletterProcessingService
import com.nexters.newsletter.service.RssContentService
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProcessControllerTest {
    private val contentRepository = mockk<ContentRepository>()
    private val contentAnalysisService = mockk<ContentAnalysisService>()
    private val reservedKeywordRepository = mockk<ReservedKeywordRepository>()
    private val contentKeywordMappingRepository = mockk<ContentKeywordMappingRepository>()
    private val summaryRepository = mockk<SummaryRepository>()
    private val candidateKeywordRepository = mockk<CandidateKeywordRepository>()
    private val exposureContentService = mockk<ExposureContentService>()
    private val rssContentService = mockk<RssContentService>()
    private val newsletterProcessingService = mockk<NewsletterProcessingService>()
    private val rateLimiterService = mockk<GeminiRateLimiterService>()

    private val controller =
        ProcessController(
            contentRepository = contentRepository,
            contentAnalysisService = contentAnalysisService,
            reservedKeywordRepository = reservedKeywordRepository,
            contentKeywordMappingRepository = contentKeywordMappingRepository,
            summaryRepository = summaryRepository,
            candidateKeywordRepository = candidateKeywordRepository,
            exposureContentService = exposureContentService,
            rssContentService = rssContentService,
            newsletterProcessingService = newsletterProcessingService,
            rateLimiterService = rateLimiterService,
        )

    @Test
    fun `get content summaries includes quality metadata`() {
        val content = sampleContent(1L)
        val pageable = PageRequest.of(0, 10)
        val summary =
            Summary(
                id = 100L,
                content = content,
                title = "사람 같은 제목",
                summarizedContent = "요약 본문",
                qualityScore = 8,
                qualityReason = "사람이 편집한 문장처럼 자연스럽습니다.",
                retryCount = 1,
                model = "gemini-2.5-flash",
            )

        every { contentRepository.findById(1L) } returns Optional.of(content)
        every { summaryRepository.findByContent(content, pageable) } returns PageImpl(listOf(summary), pageable, 1)

        val response = controller.getContentSummaries(1L, pageable)
        val body = assertNotNull(response.body)

        assertEquals(8, body.content.single().qualityScore)
        assertEquals("사람이 편집한 문장처럼 자연스럽습니다.", body.content.single().qualityReason)
        assertEquals(1, body.content.single().retryCount)
    }

    @Test
    fun `auto process content returns latest summary quality metadata`() {
        val content = sampleContent(2L)
        val exposureContent =
            ExposureContent(
                id = 77L,
                content = content,
                provocativeKeyword = "kotlin",
                provocativeHeadline = "사람 같은 제목",
                summaryContent = "요약 본문",
            )
        val summary =
            Summary(
                id = 200L,
                content = content,
                title = "사람 같은 제목",
                summarizedContent = "요약 본문",
                qualityScore = 9,
                qualityReason = "제목과 요약 모두 자연스럽습니다.",
                retryCount = 0,
                model = "gemini-2.5-flash",
            )

        every { contentRepository.findById(2L) } returns Optional.of(content)
        every { newsletterProcessingService.processExistingContent(content) } returns exposureContent
        every { contentAnalysisService.getPrioritizedSummaryByContent(content) } returns listOf(summary)

        val response = controller.autoProcessContent(2L)
        val body = assertNotNull(response.body)

        assertEquals(true, body.success)
        assertEquals(77L, body.exposureContentId)
        assertEquals(9, body.qualityScore)
        assertEquals("제목과 요약 모두 자연스럽습니다.", body.qualityReason)
        assertEquals(0, body.retryCount)
    }

    private fun sampleContent(id: Long): Content =
        Content(
            id = id,
            title = "샘플 제목",
            content = "샘플 본문",
            newsletterName = "newsletter",
            originalUrl = "https://example.com/$id",
            publishedAt = LocalDate.of(2026, 4, 2),
        )
}
