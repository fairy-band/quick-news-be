package com.nexters.external.service

import com.nexters.external.dto.GeminiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContentAnalysisResponseParserTest {
    private val parser = ContentAnalysisResponseParser()

    @Test
    fun `parses single auto generation response`() {
        val result =
            parser.parseAutoGenerationResponse(
                """
                {
                  "summary": "핵심 요약",
                  "provocativeHeadlines": ["첫 번째 헤드라인", "두 번째 헤드라인"],
                  "matchedKeywords": ["kotlin", "redis"]
                }
                """.trimIndent(),
                GeminiModel.TWO_FIVE_FLASH,
            )

        assertNotNull(result)
        assertEquals("핵심 요약", result.summary)
        assertEquals(listOf("첫 번째 헤드라인", "두 번째 헤드라인"), result.provocativeHeadlines)
        assertEquals(listOf("kotlin", "redis"), result.matchedKeywords)
    }

    @Test
    fun `parses batch auto generation response`() {
        val result =
            parser.parseBatchAutoGenerationResponse(
                """
                {
                  "results": [
                    {
                      "contentId": "1",
                      "summary": "요약1",
                      "provocativeHeadlines": ["헤드라인1"],
                      "matchedKeywords": ["kotlin"]
                    },
                    {
                      "contentId": "2",
                      "summary": "요약2",
                      "provocativeHeadlines": ["헤드라인2"],
                      "matchedKeywords": ["redis"]
                    }
                  ]
                }
                """.trimIndent(),
                GeminiModel.TWO_FIVE_FLASH,
            )

        assertNotNull(result)
        assertEquals(2, result.results.size)
        assertEquals("요약1", result.results.getValue("1").summary)
        assertEquals(listOf("redis"), result.results.getValue("2").matchedKeywords)
    }

    @Test
    fun `parses single evaluation response`() {
        val result =
            parser.parseEvaluationResponse(
                """
                {
                  "score": 8,
                  "reason": "사람이 다듬은 제목처럼 자연스럽습니다.",
                  "aiLikePatterns": ["없음"],
                  "recommendedFix": "현 상태 유지",
                  "passed": true,
                  "retryCount": 0
                }
                """.trimIndent(),
                GeminiModel.TWO_FIVE_FLASH,
            )

        assertNotNull(result)
        assertEquals(8, result.score)
        assertEquals("사람이 다듬은 제목처럼 자연스럽습니다.", result.reason)
        assertTrue(result.passed)
        assertEquals(0, result.retryCount)
    }

    @Test
    fun `parses batch evaluation response`() {
        val result =
            parser.parseBatchEvaluationResponse(
                """
                {
                  "results": [
                    {
                      "contentId": "1",
                      "score": 9,
                      "reason": "자연스럽습니다.",
                      "aiLikePatterns": [],
                      "recommendedFix": "유지",
                      "passed": true,
                      "retryCount": 0
                    },
                    {
                      "contentId": "2",
                      "score": 4,
                      "reason": "표현이 상투적입니다.",
                      "aiLikePatterns": ["클릭베이트"],
                      "recommendedFix": "기술 맥락을 더 드러내세요.",
                      "passed": false,
                      "retryCount": 0
                    }
                  ]
                }
                """.trimIndent(),
                GeminiModel.TWO_FIVE_FLASH,
            )

        assertNotNull(result)
        assertEquals(2, result.results.size)
        assertTrue(result.results.getValue("1").passed)
        assertFalse(result.results.getValue("2").passed)
        assertEquals(listOf("클릭베이트"), result.results.getValue("2").aiLikePatterns)
    }
}
