package com.nexters.newsletterfeeder.service

import com.google.genai.types.GenerateContentResponse
import com.nexters.newsletterfeeder.apiclient.GeminiClient
import com.nexters.newsletterfeeder.dto.GeminiModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SummaryServiceTest {
    private val geminiClient = mockk<GeminiClient>()
    private val sut = SummaryService(geminiClient)

    @BeforeEach
    fun setUp() {
        io.mockk.clearMocks(geminiClient)
    }

    @Test
    fun `첫 번째 모델이 null을 반환하면 두 번째 모델로 재시도한다`() {
        val content = "테스트 콘텐츠"
        val validJsonResponse = """{"summary":"테스트 요약","provocativeKeywords":["대박","미쳤다"]}"""
        val mockResponse = mockk<GenerateContentResponse>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) } returns null
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH, content) } returns mockResponse

        val result = sut.getSummary(content)

        assertEquals("테스트 요약", result.summary)
        assertEquals(listOf("대박", "미쳤다"), result.provocativeKeywords)
    }

    @Test
    fun `모든 모델이 null을 반환하면 빈 SummaryResult를 반환한다`() {
        val content = "테스트 콘텐츠"

        every { geminiClient.requestSummary(any(), content) } returns null

        val result = sut.getSummary(content)

        assertEquals("", result.summary)
        assertEquals(emptyList<String>(), result.provocativeKeywords)

        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) }
        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH, content) }
        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_FIVE_FLASH, content) }
    }

    @Test
    fun `첫 번째 모델이 정상 응답하면 재시도하지 않는다`() {
        val content = "테스트 콘텐츠"
        val validJsonResponse = """{"summary":"첫 번째 요약","provocativeKeywords":["레전드"]}"""
        val mockResponse = mockk<GenerateContentResponse>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) } returns mockResponse

        val result = sut.getSummary(content)

        assertEquals("첫 번째 요약", result.summary)
        assertEquals(listOf("레전드"), result.provocativeKeywords)

        verify(exactly = 1) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH_LITE, content) }
        verify(exactly = 0) { geminiClient.requestSummary(GeminiModel.TWO_ZERO_FLASH, content) }
        verify(exactly = 0) { geminiClient.requestSummary(GeminiModel.TWO_FIVE_FLASH, content) }
    }
}
