package com.nexters.external.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentSourceValidatorTest {
    @Test
    fun `rejects transient translation file placeholders`() {
        assertTrue(ContentSourceValidator.isInvalidSource("(cat /tmp/trans_107.md)"))
        assertTrue(ContentSourceValidator.isInvalidSource("  (cat /tmp/trans_18393.md)  "))
    }

    @Test
    fun `keeps real and short source text valid`() {
        assertFalse(ContentSourceValidator.isInvalidSource("실제 원문 본문입니다."))
        assertFalse(ContentSourceValidator.isInvalidSource("(cat /tmp/other.md)와 관련된 설명"))
        assertFalse(ContentSourceValidator.isInvalidSource("짧은 공지"))
    }
}
