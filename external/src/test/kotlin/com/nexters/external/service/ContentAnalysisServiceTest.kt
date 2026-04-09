package com.nexters.external.service

import com.google.genai.types.GenerateContentResponse
import com.nexters.external.config.AiGeminiContentAnalysisProperties
import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentGenerationAttempt
import com.nexters.external.entity.Summary
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentGenerationAttemptRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentAnalysisServiceTest {
    private val rateLimiterService = mockk<GeminiRateLimiterService>()
    private val summaryRepository = mockk<SummaryRepository>()
    private val reservedKeywordRepository = mockk<ReservedKeywordRepository>()
    private val candidateKeywordRepository = mockk<CandidateKeywordRepository>(relaxed = true)
    private val contentKeywordMappingRepository = mockk<ContentKeywordMappingRepository>(relaxed = true)
    private val contentGenerationAttemptRepository = mockk<ContentGenerationAttemptRepository>()

    private lateinit var service: ContentAnalysisService

    private val savedAttempts = mutableListOf<ContentGenerationAttempt>()
    private val savedSummaries = mutableListOf<Summary>()

    @BeforeTest
    fun setUp() {
        savedAttempts.clear()
        savedSummaries.clear()

        every { reservedKeywordRepository.findAll() } returns emptyList()
        every { reservedKeywordRepository.findByNameIn(any()) } returns emptyList()
        every { summaryRepository.findByContent(any<Content>()) } returns emptyList()
        every { summaryRepository.save(any()) } answers {
            firstArg<Summary>().also(savedSummaries::add)
        }
        every { contentGenerationAttemptRepository.save(any()) } answers {
            firstArg<ContentGenerationAttempt>().also(savedAttempts::add)
        }

        service =
            ContentAnalysisService(
                rateLimiterService = rateLimiterService,
                summaryRepository = summaryRepository,
                reservedKeywordRepository = reservedKeywordRepository,
                candidateKeywordRepository = candidateKeywordRepository,
                contentKeywordMappingRepository = contentKeywordMappingRepository,
                contentGenerationAttemptRepository = contentGenerationAttemptRepository,
                contentAnalysisProperties =
                    AiGeminiContentAnalysisProperties().apply {
                        minNaturalnessScore = 7
                        maxRegenerationAttempts = 1
                    },
            )
    }

    @Test
    fun `accepted on first pass saves one accepted attempt and summary quality`() {
        val content = sampleContent(id = 1L)

        every {
            rateLimiterService.executeAutoGenerationWithRateLimit(any(), GeminiModel.TWO_FIVE_FLASH, content.content)
        } returns mockResponse("""{"summary":"요약","provocativeHeadlines":["자연스러운 헤드라인"],"matchedKeywords":["kotlin"]}""")
        every {
            rateLimiterService.executeAutoEvaluationWithRateLimit(GeminiModel.TWO_FIVE_FLASH, any())
        } returns mockResponse(
            """{"score":8,"reason":"사람이 다듬은 문장처럼 자연스럽습니다.","aiLikePatterns":[],"recommendedFix":"유지","passed":true,"retryCount":0}""",
        )

        val result = service.analyzeAndSave(content)

        assertEquals(8, result.qualityScore)
        assertTrue(result.passed)
        assertEquals(1, savedAttempts.size)
        assertTrue(savedAttempts.single().accepted)
        assertEquals(1, savedSummaries.size)
        assertEquals(8, savedSummaries.single().qualityScore)
        assertEquals("사람이 다듬은 문장처럼 자연스럽습니다.", savedSummaries.single().qualityReason)
        assertEquals(0, savedSummaries.single().retryCount)
    }

    @Test
    fun `below threshold single retry then accept stores failed and accepted attempts`() {
        val content = sampleContent(id = 2L)

        every {
            rateLimiterService.executeAutoGenerationWithRateLimit(any(), GeminiModel.TWO_FIVE_FLASH, content.content)
        } returnsMany
            listOf(
                mockResponse("""{"summary":"요약1","provocativeHeadlines":["헤드라인1"],"matchedKeywords":["kotlin"]}"""),
                mockResponse("""{"summary":"요약2","provocativeHeadlines":["헤드라인2"],"matchedKeywords":["kotlin"]}"""),
            )
        every {
            rateLimiterService.executeAutoEvaluationWithRateLimit(GeminiModel.TWO_FIVE_FLASH, any())
        } returnsMany
            listOf(
                mockResponse(
                    """{"score":5,"reason":"표현이 너무 AI스럽습니다.","aiLikePatterns":["상투적 표현"],"recommendedFix":"대상을 더 구체화하세요.","passed":false,"retryCount":0}""",
                ),
                mockResponse(
                    """{"score":8,"reason":"재생성 후 훨씬 자연스럽습니다.","aiLikePatterns":[],"recommendedFix":"유지","passed":true,"retryCount":1}""",
                ),
            )

        val result = service.analyzeAndSave(content)

        assertEquals(2, savedAttempts.size)
        assertFalse(savedAttempts.first().accepted)
        assertTrue(savedAttempts.last().accepted)
        assertEquals(0, savedAttempts.first().retryCount)
        assertEquals(1, savedAttempts.last().retryCount)
        assertEquals(8, result.qualityScore)
        assertEquals(1, result.retryCount)
    }

    @Test
    fun `batch partial retry retries only failing item individually`() {
        val content1 = sampleContent(id = 10L, body = "첫 번째 콘텐츠")
        val content2 = sampleContent(id = 11L, body = "두 번째 콘텐츠")

        every {
            rateLimiterService.executeBatchAutoGenerationWithRateLimit(any(), GeminiModel.TWO_FIVE_FLASH, any())
        } returns mockResponse(
            """{"results":[{"contentId":"10","summary":"요약1","provocativeHeadlines":["헤드라인1"],"matchedKeywords":["kotlin"]},{"contentId":"11","summary":"요약2","provocativeHeadlines":["헤드라인2"],"matchedKeywords":["redis"]}]}""",
        )
        every {
            rateLimiterService.executeBatchAutoEvaluationWithRateLimit(GeminiModel.TWO_FIVE_FLASH, any())
        } returns mockResponse(
            """{"results":[{"contentId":"10","score":8,"reason":"좋습니다.","aiLikePatterns":[],"recommendedFix":"유지","passed":true,"retryCount":0},{"contentId":"11","score":4,"reason":"표현이 뻔합니다.","aiLikePatterns":["클릭베이트"],"recommendedFix":"핵심 대상을 드러내세요.","passed":false,"retryCount":0}]}""",
        )
        every {
            rateLimiterService.executeAutoGenerationWithRateLimit(any(), GeminiModel.TWO_FIVE_FLASH, content2.content)
        } returns mockResponse("""{"summary":"재생성 요약","provocativeHeadlines":["재생성 헤드라인"],"matchedKeywords":["redis"]}""")
        every {
            rateLimiterService.executeAutoEvaluationWithRateLimit(GeminiModel.TWO_FIVE_FLASH, any())
        } returns mockResponse(
            """{"score":8,"reason":"재생성 후 자연스럽습니다.","aiLikePatterns":[],"recommendedFix":"유지","passed":true,"retryCount":1}""",
        )

        val result = service.analyzeBatchAndSave(listOf(content1, content2))

        assertEquals(2, result.size)
        assertEquals(0, result.getValue("10").retryCount)
        assertEquals(1, result.getValue("11").retryCount)
        assertEquals(3, savedAttempts.size)
        assertEquals(2, savedSummaries.size)
        assertEquals(2, savedSummaries.count { it.qualityScore == 8 })
    }

    @Test
    fun `all rate limited generation throws rate limit exception`() {
        every {
            rateLimiterService.executeAutoGenerationWithRateLimit(any(), GeminiModel.TWO_FIVE_FLASH, any())
        } throws RateLimitExceededException("limited", "RPM", GeminiModel.TWO_FIVE_FLASH.modelName)
        every {
            rateLimiterService.executeAutoGenerationWithRateLimit(any(), GeminiModel.TWO_FIVE_FLASH_LITE, any())
        } throws RateLimitExceededException("limited", "RPM", GeminiModel.TWO_FIVE_FLASH_LITE.modelName)

        assertFailsWith<RateLimitExceededException> {
            service.analyzeContent("예시 콘텐츠", emptyList())
        }
    }

    private fun sampleContent(
        id: Long,
        body: String = "Kotlin과 Redis를 다루는 본문",
    ): Content =
        Content(
            id = id,
            title = "샘플 제목",
            content = body,
            newsletterName = "newsletter",
            originalUrl = "https://example.com/$id",
            publishedAt = LocalDate.of(2026, 4, 2),
        )

    private fun mockResponse(text: String): GenerateContentResponse {
        val response = mockk<GenerateContentResponse>()
        every { response.text() } returns text
        return response
    }
}
