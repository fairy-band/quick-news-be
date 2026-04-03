package com.nexters.external.service

import com.nexters.external.dto.ContentEvaluationResult
import com.nexters.external.dto.GeminiModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentAnalysisPromptFactoryTest {
    private val promptFactory = ContentAnalysisPromptFactory()

    @Test
    fun `single auto prompt removes legacy keyword outputs and adds human-like guidance`() {
        val prompt =
            promptFactory.buildAutoContentGenerationPrompt(
                inputKeywords = listOf("kotlin", "redis"),
                content = "Kotlin과 Redis를 활용한 캐시 전략을 설명하는 예시 콘텐츠",
            )

        assertFalse(prompt.contains("suggestedKeywords"))
        assertFalse(prompt.contains("provocativeKeywords"))
        assertTrue(prompt.contains("22-38자 내외"))
        assertTrue(prompt.contains("권장 헤드라인 예시"))
        assertTrue(prompt.contains("권장하지 않는 헤드라인 예시"))
        assertTrue(prompt.contains("상투적 클릭베이트"))
    }

    @Test
    fun `batch auto prompt keeps same human-like constraints as single prompt`() {
        val prompt =
            promptFactory.buildBatchAutoContentGenerationPrompt(
                inputKeywords = listOf("kotlin"),
                contentItems =
                    listOf(
                        com.nexters.external.dto.BatchContentItem(
                            contentId = "1",
                            content = "예시 콘텐츠",
                        ),
                    ),
            )

        assertFalse(prompt.contains("suggestedKeywords"))
        assertFalse(prompt.contains("provocativeKeywords"))
        assertTrue(prompt.contains("22-38자 내외"))
        assertTrue(prompt.contains("권장 헤드라인 예시"))
        assertTrue(prompt.contains("권장하지 않는 헤드라인 예시"))
        assertTrue(prompt.contains("실제 에디터가 제목을 다듬은 듯한"))
    }

    @Test
    fun `prompt revision suggestion prompt includes original prompt and structured evaluation result`() {
        val prompt =
            promptFactory.buildPromptRevisionSuggestionPrompt(
                originalPrompt = "기존 생성 프롬프트",
                evaluationResult =
                    ContentEvaluationResult(
                        score = 5,
                        reason = "표현이 뻔합니다.",
                        aiLikePatterns = listOf("과장된 문구", "상투적 표현"),
                        recommendedFix = "구체적인 대상과 맥락을 드러내세요.",
                        passed = false,
                        retryCount = 1,
                        usedModel = GeminiModel.TWO_FIVE_FLASH,
                    ),
            )

        assertTrue(prompt.contains("기존 생성 프롬프트"))
        assertTrue(prompt.contains("\"score\":5"))
        assertTrue(prompt.contains("\"retryCount\":1"))
        assertTrue(prompt.contains("구체적인 대상과 맥락을 드러내세요."))
    }
}
