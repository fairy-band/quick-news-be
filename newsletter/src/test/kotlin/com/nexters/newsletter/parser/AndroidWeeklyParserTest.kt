package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AndroidWeeklyParserTest {
    private val parser = AndroidWeeklyParser()

    @Test
    fun `should identify Android Weekly newsletter by sender`() {
        assertTrue(parser.isTarget("AndroidWeekly.net"))
        assertTrue(parser.isTarget("contact@androidweekly.net"))
        assertTrue(parser.isTarget("\"AndroidWeekly.net\" <contact@androidweekly.net>"))
        assertFalse(parser.isTarget("kotlin@kotlinweekly.net"))
    }

    @Test
    fun `should parse Android Weekly content with articles`() {
        val content =
            """
            Plain Text: View in web browser 685 July 27th, 2025 Articles & Tutorials Sponsored 🚨 Firebase Dynamic Links is shutting down Deep links power core user journeys, but Firebase Dynamic Links will shut down this August. Switch to the Airbridge DeepLink Plan, a powerful SDK-based solution with full support for Android and iOS. It's free for up to 10K MAU. Whether you're migrating existing links or starting from scratch, now is the time to build smarter and with less stress. Easy to integrate. Fully documented.
            Ending Android USB Freezes
            Tom Mulcahy
            Tom Mulcahy describes how blocking in AOSP's USB stack caused Android app freezes, and how Block's upstream Android 16 patches resolved it, boosting performance by ~40%.
            https://example.com/usb-freezes
            How to Encrypt Your Room Database in Android Using SQLCipher
            Pouya Heydari
            Pouya Heydari shows how to secure Room by generating an SQLCipher passphrase, storing it with EncryptedSharedPreferences, and configuring Room with a SupportFactory to enable transparent encryption.
            https://example.com/room-encryption
            Libraries & Code
            TimelineView
            A synchronized dual-view timeline visualization component for Android with native Compose support.
            https://github.com/example/timeline
            """.trimIndent()

        val results = parser.parse(content)

        assertTrue(results.isNotEmpty())

        // Check first article
        val firstArticle = results.find { it.title.contains("Ending Android USB Freezes") }
        assertNotNull(firstArticle)
        assertEquals("Articles & Tutorials", firstArticle?.section)
        assertTrue(firstArticle?.content?.contains("Tom Mulcahy") == true)
        assertEquals("https://example.com/usb-freezes", firstArticle?.link)

        // Check second article
        val secondArticle = results.find { it.title.contains("How to Encrypt Your Room Database") }
        assertNotNull(secondArticle)
        assertEquals("Articles & Tutorials", secondArticle?.section)
        assertTrue(secondArticle?.content?.contains("Pouya Heydari") == true)

        // Check library
        val library = results.find { it.title == "TimelineView" }
        assertNotNull(library)
        assertEquals("Libraries & Code", library?.section)
    }

    @Test
    fun `should extract issue information correctly`() {
        val content =
            """
            Plain Text: View in web browser 687 August 10th, 2025 Articles & Tutorials
            Test Article
            Test Author
            Test description
            https://example.com/test
            """.trimIndent()

        val results = parser.parse(content)

        assertTrue(results.isNotEmpty())
        val article = results.first()
        assertTrue(article.content.contains("Issue #687"))
        assertTrue(article.content.contains("August 10th, 2025"))
    }

    @Test
    fun `should filter out sponsored and job sections`() {
        val content =
            """
            Plain Text: View in web browser 685 July 27th, 2025
            Articles & Tutorials
            Good Article
            Author Name
            Article description
            https://example.com/article
            Sponsored
            Sponsored Content
            Sponsor Name
            Sponsored description
            https://example.com/sponsored
            Jobs
            Senior Android Engineer
            Company Name
            Job description
            https://example.com/job
            """.trimIndent()

        val results = parser.parse(content)

        // Should have article but not sponsored or job content
        assertTrue(results.any { it.title == "Good Article" })
        assertFalse(results.any { it.title == "Sponsored Content" })
        assertFalse(results.any { it.title == "Senior Android Engineer" })
    }

    @Test
    fun `should handle malformed content gracefully`() {
        val emptyContent = ""
        assertTrue(parser.parse(emptyContent).isEmpty())

        val noPlainText = "HTML: <html>...</html>"
        val results = parser.parse(noPlainText)
        assertTrue(results.isEmpty() || results.isNotEmpty()) // Should not throw exception

        val noSections = "Plain Text: Just some random text without sections"
        assertTrue(parser.parse(noSections).isEmpty())
    }

    @Test
    fun `should clean URLs properly`() {
        val content =
            """
            Plain Text: View in web browser 685 July 27th, 2025
            Articles & Tutorials
            Test Article
            Test Author
            Test description
            https://example.com/test?param=value&other=123
            """.trimIndent()

        val results = parser.parse(content)
        assertTrue(results.isNotEmpty())
        assertEquals("https://example.com/test?param=value&other=123", results.first().link)
    }
}
