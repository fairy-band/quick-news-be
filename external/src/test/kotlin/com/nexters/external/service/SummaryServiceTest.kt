package com.nexters.external.service

import com.google.genai.types.GenerateContentResponse
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.Content
import com.nexters.external.entity.Summary
import com.nexters.external.repository.SummaryRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SummaryServiceTest {
    private val geminiClient = mockk<GeminiClient>()
    private val summaryRepository = mockk<SummaryRepository>()
    private val sut = SummaryService(geminiClient, summaryRepository)

    @BeforeEach
    fun setUp() {
        clearMocks(geminiClient, summaryRepository)
    }

    @Test
    fun `첫 번째 모델이 null을 반환하면 두 번째 모델로 재시도한다`() {
        val content = "테스트 콘텐츠"
        val validJsonResponse = """{"summary":"테스트 요약","provocativeHeadlines":["충격! 이것만 알면 성공한다","당신이 몰랐던 비밀 공개"]}"""
        val mockResponse = mockk<GenerateContentResponse>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) } returns null
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH, content) } returns mockResponse

        val result = sut.summarize(content)

        assertEquals("테스트 요약", result.summary)
        assertEquals(listOf("충격! 이것만 알면 성공한다", "당신이 몰랐던 비밀 공개"), result.provocativeHeadlines)
        assertEquals(GeminiModel.TWO_ZERO_FLASH, result.usedModel)
    }

    @Test
    fun `모든 모델이 null을 반환하면 빈 SummaryResult를 반환한다`() {
        val content = "테스트 콘텐츠"

        every { geminiClient.requestSummary(any(), content) } returns null

        val result = sut.summarize(content)

        assertEquals("", result.summary)
        assertEquals(emptyList<String>(), result.provocativeHeadlines)
        assertEquals(null, result.usedModel)

        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) }
        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH, content) }
        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_FIVE_FLASH, content) }
    }

    @Test
    fun `첫 번째 모델이 정상 응답하면 재시도하지 않는다`() {
        val content = "테스트 콘텐츠"
        val validJsonResponse = """{"summary":"첫 번째 요약","provocativeHeadlines":["전문가들도 놀란 비밀 공개"]}"""
        val mockResponse = mockk<GenerateContentResponse>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) } returns mockResponse

        val result = sut.summarize(content)

        assertEquals("첫 번째 요약", result.summary)
        assertEquals(listOf("전문가들도 놀란 비밀 공개"), result.provocativeHeadlines)
        assertEquals(GeminiModel.TWO_ZERO_FLASH_LITE, result.usedModel)

        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) }
        verify(exactly = 0) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH, content) }
        verify(exactly = 0) { geminiClient.requestSummary(GeminiModel.TWO_FIVE_FLASH, content) }
    }

    @Test
    fun `summarizeAndSave는 요약 결과를 저장하고 반환한다`() {
        // given
        val contentEntity =
            Content(
                id = 1L,
                newsletterSourceId = "test-source",
                title = "컨텐츠 제목",
                content = "This is test content for summarization",
                newsletterName = "Test Newsletter",
                originalUrl = "https://test.com",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        val validJsonResponse = """{"summary":"요약된 내용입니다","provocativeHeadlines":["흥미로운 제목"]}"""
        val mockResponse = mockk<GenerateContentResponse>()
        val summarySlot = slot<Summary>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, contentEntity.content) } returns mockResponse
        every { summaryRepository.save(capture(summarySlot)) } returns
            Summary(
                id = 1L,
                content = contentEntity,
                title = contentEntity.title,
                summarizedContent = "요약된 내용입니다",
                model = "gemini-2.0-flash-lite"
            )

        // when
        val result = sut.summarizeAndSave(contentEntity)

        // then
        assertEquals("요약된 내용입니다", result.summary)
        assertEquals(listOf("흥미로운 제목"), result.provocativeHeadlines)
        assertEquals(GeminiModel.TWO_ZERO_FLASH_LITE, result.usedModel)
        assertEquals("컨텐츠 제목", summarySlot.captured.title)
        assertEquals("요약된 내용입니다", summarySlot.captured.summarizedContent)
        assertEquals("gemini-2.0-flash-lite", summarySlot.captured.model)
    }
}
