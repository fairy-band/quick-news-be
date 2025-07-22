package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class BytesParserTest {
    private val sut = BytesParser()

    @Test
    fun `isTarget should return true for Bytes emails`() {
        assertTrue(sut.isTarget("Bytes <noreply@ui.dev>"))
        assertTrue(sut.isTarget("ui.dev team <support@ui.dev>"))
        assertTrue(sut.isTarget("bytes.dev newsletter"))
        assertTrue(sut.isTarget("Bytes Newsletter"))
    }

    @Test
    fun `isTarget should return false for non-Bytes emails`() {
        assertFalse(sut.isTarget("JavaScript Weekly <jsw@peterc.org>"))
        assertFalse(sut.isTarget("Donny Wals <donny@donnywals.com>"))
        assertFalse(sut.isTarget("random@example.com"))
    }

    @Test
    fun `debug simple parsing test`() {
        // 더 간단한 HTML로 테스트
        val simpleHtml =
            """
            <html>
                <body>
                    <p>Welcome to #409.</p>
                    <h2>The Main Thing</h2>
                    <p><a href="https://example.com/main">Main Article</a></p>
                    <h2>Cool Bits</h2>
                    <ol>
                        <li><a href="https://example.com/cool1">Cool Article 1</a></li>
                        <li><a href="https://example.com/cool2">Cool Article 2</a></li>
                    </ol>
                </body>
            </html>
            """.trimIndent()

        // when
        val result = sut.parse(simpleHtml)

        // then
        println("Simple test result count: ${result.size}")
        result.forEach { article ->
            println("- ${article.title} | ${article.section} | ${article.link}")
        }

        assertTrue(result.isNotEmpty(), "Should parse at least one article from simple HTML")
    }

    @Test
    fun `parse should extract articles from Bytes newsletter`() {
        val sampleEmail =
            """
            Content-Type: text/html; charset="utf-8"

            <!DOCTYPE html>
            <html>
                <body>
                    <p>Welcome to #409.</p>

                    <h2>The Main Thing</h2>
                    <h3>America's NextJS Top Model</h3>
                    <p>The Triangle Gods blessed us with <a href="https://nextjs.org/blog/next-15-4">Next.js 15.4</a> on Monday, and it comes with two riveting updates:</p>
                    <p>Check out the <a href="https://aretweturboyet.com/">turbo build times</a> and see if they live up to the hype.</p>

                    <h2>Cool Bits</h2>
                    <ol>
                        <li>
                            <a href="https://hashbrown.dev/blog/2025-07-16-hashbrown-v-0-2-0">Hashbrown just released v0.2</a> of its framework for building AI-powered UIs in Angular and React.
                        </li>
                        <li>
                            Leerob wrote about <a href="https://leerob.com/vercel">5 things he learned from 5 years at Vercel</a>. Shoutout to the 🐐.
                        </li>
                        <li>
                            <a href="https://stack.convex.dev/convex-resend">Convex just launched a Resend Component</a> that lets you easily integrate Resend's DX-focused email service.
                        </li>
                    </ol>

                    <p><a href="https://workos.com/sponsor">WorkOS Sponsor Link</a></p>
                    <p><a href="https://bytes.dev/advertise">Sponsored content</a></p>
                </body>
            </html>
            """.trimIndent()

        // Debug: Test HtmlTextExtractor directly first
        println("=== Direct HtmlTextExtractor Test ===")
        val directLinks = HtmlTextExtractor.extractLinks(sampleEmail)
        println("Direct extraction found ${directLinks.size} links:")
        directLinks.forEach { (title, url) ->
            println("- '$title' -> '$url'")
        }

        // when
        val result = sut.parse(sampleEmail)

        // Debug output - this will always run
        println("=== DEBUG: Parsed Results ===")
        println("Total results: ${result.size}")
        if (result.isEmpty()) {
            println("ERROR: No results found! This is why the test is failing.")
            // Test the individual components
            println("\nTesting components:")
            println("1. isTarget test: ${sut.isTarget("Bytes <noreply@ui.dev>")}")

            // Test if HTML is detected
            val htmlDetected = HtmlTextExtractor.isHtml(sampleEmail)
            println("2. HTML detected: $htmlDetected")

            fail("No parsing results - see debug output above")
        }
        result.forEachIndexed { index, content ->
            println("[$index] Title: '${content.title}'")
            println("[$index] Link: '${content.link}'")
            println("[$index] Section: '${content.section}'")
            println("[$index] Content: '${content.content}'")
            println("---")
        }

        assertTrue(result.isNotEmpty(), "Should parse some content")

        // Verify Main Thing articles
        val mainThingArticles = result.filter { it.section == "The Main Thing" }
        println("=== DEBUG: Main Thing Articles ===")
        println("Count: ${mainThingArticles.size}")
        mainThingArticles.forEach { println("- ${it.title} | ${it.link}") }

        assertTrue(mainThingArticles.isNotEmpty(), "Should have Main Thing content")

        val nextJsArticle = mainThingArticles.find { it.title.contains("Next.js 15.4") }
        verifyArticle(
            nextJsArticle,
            "Next.js 15.4",
            "https://nextjs.org/blog/next-15-4",
            "The Main Thing"
        )

        // Verify Cool Bits articles
        val coolBitsArticles = result.filter { it.section == "Cool Bits" }
        assertTrue(coolBitsArticles.size >= 3, "Should have at least 3 Cool Bits articles")

        verifyArticle(
            coolBitsArticles.find { it.title.contains("Hashbrown") },
            "Hashbrown just released v0.2",
            "https://hashbrown.dev/blog/2025-07-16-hashbrown-v-0-2-0",
            "Cool Bits"
        )

        verifyArticle(
            coolBitsArticles.find { it.title.contains("5 things") },
            "5 things he learned from 5 years at Vercel",
            "https://leerob.com/vercel",
            "Cool Bits"
        )

        // Verify sponsor content is filtered out
        result.forEach { article ->
            assertFalse(
                article.link.contains("workos.com", ignoreCase = true) ||
                    article.link.contains("advertise", ignoreCase = true),
                "Should filter out sponsor links: ${article.link}"
            )
        }

        // Verify all articles have valid links
        result.forEach { article ->
            assertTrue(article.link.startsWith("http"), "Link should be valid URL: ${article.link}")
            assertTrue(article.title.isNotBlank(), "Title should not be blank")
            assertTrue(article.content.contains("Issue #409"), "Content should contain issue number")
        }
    }

    @Test
    fun `parse should handle empty content gracefully`() {
        val emptyContents = listOf("", "   ", "<html></html>", "<html><body></body></html>")

        emptyContents.forEach { content ->
            val result = sut.parse(content)
            assertNotNull(result, "Should handle empty content without throwing exception")
        }
    }

    @Test
    fun `parse should filter sponsor content effectively`() {
        val htmlWithSponsors =
            """
            <html>
                <body>
                    <h2>The Main Thing</h2>
                    <p><a href="https://example.com/good-article">Good JavaScript Article</a></p>
                    <p><a href="https://workos.com/sponsor">WorkOS Sponsor Link</a></p>
                    <p><a href="https://bytes.dev/advertise">Advertisement Link</a></p>
                </body>
            </html>
            """.trimIndent()

        // when
        val result = sut.parse(htmlWithSponsors)

        // then
        result.forEach { article ->
            assertFalse(
                article.link.contains("workos.com", ignoreCase = true) ||
                    article.link.contains("advertise", ignoreCase = true),
                "Should filter out sponsor links: ${article.link}"
            )
            assertFalse(
                article.title.contains("sponsor", ignoreCase = true) ||
                    article.title.contains("advertisement", ignoreCase = true),
                "Should filter out sponsor titles: ${article.title}"
            )
        }
    }

    private fun verifyArticle(
        article: MailContent?,
        expectedTitleContains: String,
        expectedLink: String,
        expectedSection: String
    ) {
        assertNotNull(article, "Article containing '$expectedTitleContains' should be found")
        assertTrue(
            article!!.title.contains(expectedTitleContains),
            "Article title should contain '$expectedTitleContains', but was '${article.title}'"
        )
        assertEquals(expectedLink, article.link, "Article link should match expected")
        assertEquals(expectedSection, article.section, "Article section should match expected")
        assertTrue(
            article.content.contains("[$expectedSection]"),
            "Article content should contain section marker"
        )
    }
}
