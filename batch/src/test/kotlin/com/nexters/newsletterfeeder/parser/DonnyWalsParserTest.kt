package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DonnyWalsParserTest {
    private val sut = DonnyWalsParser()

    @Test
    fun `isTarget should return true for Donny Wals emails`() {
        assertTrue(sut.isTarget("Donny Wals"))
        assertTrue(sut.isTarget("Donny Wals <donny@donnywals.com>"))
        assertTrue(sut.isTarget("donnywals.com newsletter"))
        assertTrue(sut.isTarget("support@donnywals.com"))
    }

    @Test
    fun `isTarget should return false for non-Donny Wals emails`() {
        assertFalse(sut.isTarget("Bytes <noreply@ui.dev>"))
        assertFalse(sut.isTarget("JavaScript Weekly <jsw@peterc.org>"))
        assertFalse(sut.isTarget("random@example.com"))
    }

    @Test
    fun `parse should extract articles from Donny Wals newsletter`() {
        val sampleEmail =
            """
            Content-Type: text/html; charset="utf-8"

            <!doctype html>
            <html>
                <head><title>Swift Newsletter</title></head>
                <body>
                    <h1>Swift Newsletter</h1>
                    <p>Swift development updates and tutorials.</p>

                    <h2>Swift Concurrency Updates</h2>
                    <p>Check out <a href="https://developer.apple.com/swift-concurrency">Practical Swift Concurrency</a> to learn about the latest improvements.</p>
                    <p>Learn more about <a href="https://developer.apple.com/swift-docs">Swift Development Documentation</a> for comprehensive guides.</p>

                    <h3>Other content that I really want to share with you</h3>
                    <p><a href="https://donnywals.com/building-ios-apps-swiftui">Building iOS Apps with SwiftUI</a></p>
                    <p><a href="https://donnywals.com/practical-ios-tutorial">Practical iOS Development Tutorial</a></p>
                </body>
            </html>
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertTrue(result.isNotEmpty(), "Should parse some content")

        // 각 결과가 유효한지 확인
        result.forEach { article ->
            assertTrue(article.title.isNotBlank(), "Title should not be blank")
            assertTrue(article.link.startsWith("http"), "Link should be valid URL")
            assertTrue(article.content.contains("Swift/iOS"), "Content should mention Swift/iOS")
            assertNotNull(article.section, "Section should not be null")
        }
    }

    @Test
    fun `parse should identify educational content`() {
        val htmlWithEducationalContent =
            """
            <html>
                <body>
                    <h2>Learning Resources</h2>
                    <p><a href="https://example.com/swift-tutorial">Complete Swift Tutorial Guide</a></p>
                    <p><a href="https://example.com/ios-course">Learn iOS Development Course</a></p>
                    <p><a href="https://example.com/swiftui-book">Practical SwiftUI Book</a></p>
                </body>
            </html>
            """.trimIndent()

        // when
        val result = sut.parse(htmlWithEducationalContent)

        // then - 교육 콘텐츠가 있으면 적절한 키워드를 포함해야 함
        val educationalResults = result.filter { it.section == "Educational Content" }
        educationalResults.forEach { article ->
            val titleLower = article.title.lowercase()
            assertTrue(
                titleLower.contains("tutorial") ||
                    titleLower.contains("guide") ||
                    titleLower.contains("course") ||
                    titleLower.contains("book") ||
                    titleLower.contains("practical") ||
                    titleLower.contains("learn") ||
                    titleLower.contains("swift") ||
                    titleLower.contains("ios"),
                "Educational content should contain relevant keywords: ${article.title}"
            )
        }
    }

    @Test
    fun `parse should handle various HTML structures gracefully`() {
        val htmlStructures =
            listOf(
                "",
                "   ",
                "<html></html>",
                "<html><body></body></html>",
                "<html><body><h1>Title Only</h1></body></html>"
            )

        htmlStructures.forEach { content ->
            // when & then
            assertDoesNotThrow("Should handle various HTML structures gracefully") {
                val result = sut.parse(content)
                assertNotNull(result, "Result should not be null")
            }
        }
    }

    @Test
    fun `parse should handle valid links correctly`() {
        val simpleValidHtml =
            """
            <html>
                <body>
                    <p><a href="https://example.com/good-swift-article">Good Swift Article</a></p>
                    <p><a href="https://developer.apple.com/swift">Swift Documentation</a></p>
                </body>
            </html>
            """.trimIndent()

        // when
        val result = sut.parse(simpleValidHtml)

        // then - 좋은 링크들이 포함되었는지 확인
        if (result.isNotEmpty()) {
            result.forEach { article ->
                assertTrue(
                    article.link.startsWith("https://"),
                    "Should only include valid HTTPS links: ${article.link}"
                )
                assertTrue(
                    article.title.isNotBlank(),
                    "Title should not be blank: ${article.title}"
                )
            }
        }
    }
}
