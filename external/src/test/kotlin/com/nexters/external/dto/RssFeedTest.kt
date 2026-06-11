package com.nexters.external.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RssFeedTest {
    @Test
    fun `toContentText decodes html entities and strips markup`() {
        val item =
            rssItem(
                description = "&#44544; &#47553;&#53356;: https://example.com",
                content = "<p>&#49548;&#44060; &amp; <strong>본문</strong></p>",
                author = "  Jane &amp; Team  ",
                categories = listOf(" Kotlin ", "&#50504;&#46300;&#47196;&#51060;&#46300;"),
            )

        assertEquals(
            """
            글 링크: https://example.com

            소개 & 본문

            Author: Jane & Team

            Categories: Kotlin, 안드로이드
            """.trimIndent(),
            item.toContentText(),
        )
    }

    @Test
    fun `toContentText ignores blank author and categories`() {
        val item =
            rssItem(
                description = null,
                content = null,
                author = " ",
                categories = listOf(" ", ""),
            )

        val contentText = item.toContentText()

        assertEquals("", contentText)
        assertFalse(contentText.contains("Author:"))
        assertFalse(contentText.contains("Categories:"))
    }

    @Test
    fun `toContentText does not duplicate identical description and content`() {
        val item =
            rssItem(
                description = "<p>same body</p>",
                content = "same body",
            )

        assertEquals("same body", item.toContentText())
    }

    private fun rssItem(
        description: String? = null,
        content: String? = null,
        author: String? = null,
        categories: List<String> = emptyList(),
    ): RssItem =
        RssItem(
            title = "Test RSS Item",
            description = description,
            link = "https://example.com/post",
            publishedDate = null,
            author = author,
            categories = categories,
            content = content,
        )
}
