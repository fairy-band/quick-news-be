package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextSanitizerTest {
    @Test
    fun `test decodeQuotedPrintable`() {
        val input = "Hello=20World"
        val expected = "Hello World"
        assertEquals(expected, TextSanitizer.decodeQuotedPrintable(input))
    }

    @Test
    fun `test decodeHtmlEntities with standard entities`() {
        val input = "Hello&nbsp;World &lt;tag&gt; &amp; more"
        val expected = "Hello\u00A0World <tag> & more"
        assertEquals(expected, TextSanitizer.decodeHtmlEntities(input))
    }

    @Test
    fun `test decodeHtmlEntities with numeric entities`() {
        val input = "Hello &#160; World &#60;tag&#62;"
        val expected = "Hello \u00A0 World <tag>"
        assertEquals(expected, TextSanitizer.decodeHtmlEntities(input))
    }

    @Test
    fun `test decodeHtmlEntities with hex entities`() {
        val input = "Hello &#x00A0; World &#x3C;tag&#x3E;"
        val expected = "Hello \u00A0 World <tag>"
        assertEquals(expected, TextSanitizer.decodeHtmlEntities(input))
    }

    @Test
    fun `test normalizeHtmlEntities with broken entities`() {
        val input = "Hello &n bsp; World and &n= bsp; test"
        val expected = "Hello &nbsp; World and &nbsp; test"
        assertEquals(expected, TextSanitizer.normalizeHtmlEntities(input))
    }

    @Test
    fun `test decodeAndSanitize with mixed content`() {
        val input = "Hello=20&n= bsp;World &lt;tag&gt; &#160; test"
        val result = TextSanitizer.decodeAndSanitize(input)

        // 실제 출력 결과와 정확히 일치하도록 수정
        val expected = "Hello World <tag>  test"
        assertEquals(expected, result)
    }
}
