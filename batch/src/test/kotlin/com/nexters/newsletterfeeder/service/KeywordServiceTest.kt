package com.nexters.newsletterfeeder.service

import com.google.genai.types.GenerateContentResponse
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.service.KeywordService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KeywordServiceTest {
    private val geminiClient = mockk<GeminiClient>()
    private val sut = KeywordService(geminiClient)

    @BeforeEach
    fun setUp() {
        io.mockk.clearMocks(geminiClient)
    }

    @Test
    fun `첫 번째 모델이 null을 반환하면 두 번째 모델로 재시도한다`() {
        val inputKeywords = listOf("AI", "기술")
        val content = "인공지능 기술의 발전"
        val validJsonResponse = """{"matchedKeywords":["AI","기술"],"suggestedKeywords":["인공지능","발전"],"provocativeKeywords":["대박","미쳤다"]}"""
        val mockResponse = mockk<GenerateContentResponse>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestKeywords(inputKeywords, GeminiModel.TWO_ZERO_FLASH_LITE, content) } returns null
        every { geminiClient.requestKeywords(inputKeywords, GeminiModel.TWO_ZERO_FLASH, content) } returns mockResponse

        val result = sut.extractKeywords(inputKeywords, content)

        assertEquals(listOf("AI", "기술"), result.matchedKeywords)
        assertEquals(listOf("인공지능", "발전"), result.suggestedKeywords)
        assertEquals(listOf("대박", "미쳤다"), result.provocativeKeywords)
    }

    @Test
    fun `모든 모델이 null을 반환하면 빈 KeywordResult를 반환한다`() {
        val inputKeywords = listOf("AI", "기술")
        val content = "인공지능 기술의 발전"

        every { geminiClient.requestKeywords(inputKeywords, any(), content) } returns null

        val result = sut.extractKeywords(inputKeywords, content)

        assertEquals(emptyList<String>(), result.matchedKeywords)
        assertEquals(emptyList<String>(), result.suggestedKeywords)
        assertEquals(emptyList<String>(), result.provocativeKeywords)
    }

    @Test
    fun `첫 번째 모델이 정상 응답하면 재시도하지 않는다`() {
        val inputKeywords = listOf("AI", "기술")
        val content = "인공지능 기술의 발전"
        val validJsonResponse = """{"matchedKeywords":["AI"],"suggestedKeywords":["머신러닝"],"provocativeKeywords":["레전드"]}"""
        val mockResponse = mockk<GenerateContentResponse>()

        every { mockResponse.text() } returns validJsonResponse
        every { geminiClient.requestKeywords(inputKeywords, GeminiModel.TWO_ZERO_FLASH_LITE, content) } returns mockResponse

        val result = sut.extractKeywords(inputKeywords, content)

        assertEquals(listOf("AI"), result.matchedKeywords)
        assertEquals(listOf("머신러닝"), result.suggestedKeywords)
        assertEquals(listOf("레전드"), result.provocativeKeywords)
    }
}
