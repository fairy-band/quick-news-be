package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class IlbunParserTest {
    private val sut = IlbunParser()

    @Test
    fun `should correctly identify ilbuntok emails`() {
        assertTrue(sut.isTarget("morning@ilbuntok.com"))
        assertTrue(sut.isTarget("newsletter from ilbuntok"))
        assertTrue(sut.isTarget("ILBUNTOK Daily News"))
        assertFalse(sut.isTarget("other@example.com"))
    }

    @Test
    fun `should extract required titles from ilbun html`() {
        // given

        val content = File("src/test/resources/ilbun.html").readText()

        // when
        val result = sut.parse(content)

        // then
        val requiredTitles =
            listOf(
                "트럼프의 AI 액션플랜",
                "오픈AI, ‘ChatGPT Agent’ 공개",
                "2026 월드컵 사진 사수 작전",
                "빅테크, AI 스타트업 ‘쪼개 먹기’ 신공",
                "메타, 애플 AI 핵심 인재 연이어 빼가",
                "판사가 내린 첫 ‘페어유즈’ 판정",
                "xAI, ‘AI판 폭스뉴스’로 가나",
            )
        assertEquals(result.map { it.title }, requiredTitles)
    }
}
