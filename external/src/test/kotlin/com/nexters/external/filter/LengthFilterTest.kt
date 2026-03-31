package com.nexters.external.filter

import com.nexters.external.entity.Content
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LengthFilterTest {
    @Test
    fun `content가 최소 길이보다 짧으면 필터링된다`() {
        // given
        val filter = LengthFilter(minLength = 500, maxLength = 10_000)
        val content = createContent("a".repeat(300))

        // when
        val result = filter.filter(content)

        // then
        assertFalse(result.passed)
        assertTrue(result is FilterResult.Fail)
        assertTrue((result as FilterResult.Fail).reason.contains("too short"))
    }

    @Test
    fun `content가 최대 길이보다 길면 필터링된다`() {
        // given
        val filter = LengthFilter(minLength = 500, maxLength = 10_000)
        val content = createContent("a".repeat(15_000))

        // when
        val result = filter.filter(content)

        // then
        assertFalse(result.passed)
        assertTrue(result is FilterResult.Fail)
        assertTrue((result as FilterResult.Fail).reason.contains("too long"))
    }

    @Test
    fun `content가 적절한 길이면 통과한다`() {
        // given
        val filter = LengthFilter(minLength = 500, maxLength = 10_000)
        val content = createContent("a".repeat(5_000))

        // when
        val result = filter.filter(content)

        // then
        assertTrue(result.passed)
        assertTrue(result is FilterResult.Pass)
    }

    @Test
    fun `sealed interface를 사용한 when 표현식 테스트`() {
        // given
        val filter = LengthFilter(minLength = 500, maxLength = 10_000)
        val shortContent = createContent("a".repeat(300))
        val validContent = createContent("a".repeat(5_000))

        // when & then
        when (val result = filter.filter(shortContent)) {
            is FilterResult.Pass -> fail("Should not pass")
            is FilterResult.Fail -> assertTrue(result.reason.contains("too short"))
        }

        when (val result = filter.filter(validContent)) {
            is FilterResult.Pass -> assertTrue(true)
            is FilterResult.Fail -> fail("Should pass")
        }
    }

    private fun createContent(contentText: String): Content =
        Content(
            id = 1L,
            newsletterSourceId = "test-source",
            title = "Test Title",
            content = contentText,
            newsletterName = "Test Newsletter",
            originalUrl = "https://example.com",
            publishedAt = LocalDate.now(),
            contentProvider = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
}
