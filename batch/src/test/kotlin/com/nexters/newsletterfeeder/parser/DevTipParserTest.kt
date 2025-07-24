package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevTipParserTest {
    private val sut = DevTipParser()

    @Test
    fun `isTarget should return true for Ardalis emails`() {
        assertTrue(sut.isTarget("Steve Smith - Ardalis.com <steve@ardalis.com>"))
        assertTrue(sut.isTarget("info@ardalis.com"))
        assertTrue(sut.isTarget("Ardalis"))
    }

    @Test
    fun `isTarget should return true for JavaScript Weekly emails`() {
        assertTrue(sut.isTarget("JavaScript Weekly <info@javascriptweekly.com>"))
        assertTrue(sut.isTarget("newsletter@javascriptweekly.com"))
        assertTrue(sut.isTarget("JavaScript Weekly"))
    }

    @Test
    fun `isTarget should return false for non-Ardalis emails`() {
        assertFalse(sut.isTarget("Some Other Newsletter <info@example.com>"))
        assertFalse(sut.isTarget("random@example.com"))
    }

    @Test
    fun `parse should extract Dev Tip content correctly`() {
        val sampleEmail =
            """
            MultipartContent(emailFrom=["Steve Smith - Ardalis.com" <steve@ardalis.com>], emailSubject=Dev Tip #366: Generative AI is the New Offshoring, emailReceivedDate=null, emailSentDate=2025-07-23T23:45:36, emailContentType=multipart/alternative; boundary="_----------=_MCPart_246769083", emailExtractedContent=Plain Text: It makes lots of code, cheaply. We seen this before.

            With outsourcing to offshore teams, you get lots of code written cheaply and quickly, but you often get exactly what you ask for - no more, no less. And the code quality can vary quite a bit.

            With AI-generated code, you often get the same result. You get a lot of code quickly and cheaply, but you need to be very specific about what you want, and the quality can be inconsistent.

            Both require significant oversight and management to get good results.

            The key difference? Offshore teams can learn and improve over time, building domain knowledge and relationships. AI tools are getting better, but they reset with each interaction.

            The lesson: Whether working with offshore teams or AI, success depends on:
            - Clear requirements and specifications
            - Good communication and feedback loops
            - Quality review processes
            - Understanding the strengths and limitations of your tools

            Don't expect magic from either approach - but used properly, both can be valuable parts of your development strategy.

            Steve)
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertEquals(1, result.size, "Expected 1 Dev Tip, but got ${result.size}")

        val devTip = result[0]
        assertEquals("Generative AI is the New Offshoring", devTip.title)
        assertEquals("Dev Tip", devTip.section)
        assertEquals("", devTip.link) // Dev Tips usually don't have links
        assertTrue(devTip.content.contains("Dev Tip #366"))
        assertTrue(devTip.content.contains("It makes lots of code, cheaply"))
        assertTrue(devTip.content.contains("Clear requirements and specifications"))
    }

    @Test
    fun `parse should extract JavaScript Weekly articles correctly`() {
        val sampleEmail =
            """
            MultipartContent(emailFrom=["JavaScript Weekly" <info@javascriptweekly.com>], emailSubject=JavaScript Weekly Issue #703, emailReceivedDate=null, emailSentDate=2025-07-24T14:30:15, emailContentType=multipart/alternative; boundary="_----------=_MCPart_987654321", emailExtractedContent=Plain Text: ▶ JavaScript Weekly

            Issue #703 — July 24, 2025

            ✋ Hey there! This is JavaScript Weekly, a weekly newsletter about JavaScript and the world around it.

            🔥 This Week's Highlights

            → TypeScript 5.6 RC Released
            Microsoft has released the TypeScript 5.6 Release Candidate with new features including improved template literal inference and better support for decorators.
            Link: https://devblogs.microsoft.com/typescript/announcing-typescript-5-6-rc/

            → New JavaScript Framework: QuickUI 2.0
            A new ultra-lightweight framework for building reactive UIs with vanilla JavaScript. Features component-based architecture with minimal overhead.
            Link: https://github.com/quickui/quickui

            → Node.js 20.15.0 Now Available
            The latest LTS release includes performance improvements for ES modules, better error handling, and updated V8 engine.
            Link: https://nodejs.org/en/blog/release/v20.15.0

            🛠️ Tools & Libraries

            → Vitest 2.0: Next-Gen Testing Framework
            Major update brings improved performance, better TypeScript support, and new snapshot testing capabilities.
            Link: https://vitest.dev/guide/migration.html

            → ESLint v9.0 Configuration Changes
            Breaking changes in the new major version require migration to flat config format. Migration guide available.
            Link: https://eslint.org/docs/latest/use/configure/migration-guide

            📚 Articles & Tutorials

            → "Understanding React Server Components"
            Deep dive into the new paradigm for server-side rendering in React applications.
            Link: https://react.dev/blog/2025/07/react-server-components-guide

            → "Modern JavaScript Performance Optimization"
            Comprehensive guide covering code splitting, tree shaking, and runtime optimization techniques.
            Link: https://web.dev/javascript-performance-2025

            That's all for this week!
            Thanks for reading JavaScript Weekly.

            Peter Cooper & the JavaScript Weekly team)
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertEquals(7, result.size, "Expected 7 JavaScript Weekly articles, but got ${result.size}")

        // Verify some articles
        verifyArticle(
            result[0],
            "TypeScript 5.6 RC Released",
            "https://devblogs.microsoft.com/typescript/announcing-typescript-5-6-rc/",
            "JavaScript Weekly",
            "Microsoft has released"
        )

        verifyArticle(
            result[1],
            "New JavaScript Framework: QuickUI 2.0",
            "https://github.com/quickui/quickui",
            "JavaScript Weekly",
            "ultra-lightweight framework"
        )

        verifyArticle(
            result[2],
            "Node.js 20.15.0 Now Available",
            "https://nodejs.org/en/blog/release/v20.15.0",
            "JavaScript Weekly",
            "latest LTS release"
        )
    }

    @Test
    fun `parse should handle multiple MultipartContent entries`() {
        val sampleEmail =
            """
            MultipartContent(emailFrom=["Steve Smith - Ardalis.com" <steve@ardalis.com>], emailSubject=Dev Tip #366: Generative AI is the New Offshoring, emailReceivedDate=null, emailSentDate=2025-07-23T23:45:36, emailContentType=multipart/alternative; boundary="_----------=_MCPart_246769083", emailExtractedContent=Plain Text: This is the first dev tip content.)

            MultipartContent(emailFrom=["Steve Smith - Ardalis.com" <steve@ardalis.com>], emailSubject=Dev Tip #367: Clean Code Principles, emailReceivedDate=null, emailSentDate=2025-07-24T23:45:36, emailContentType=multipart/alternative; boundary="_----------=_MCPart_246769084", emailExtractedContent=Plain Text: This is the second dev tip content.)
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertEquals(2, result.size, "Expected 2 Dev Tips, but got ${result.size}")

        assertEquals("Generative AI is the New Offshoring", result[0].title)
        assertTrue(result[0].content.contains("first dev tip content"))

        assertEquals("Clean Code Principles", result[1].title)
        assertTrue(result[1].content.contains("second dev tip content"))
    }

    @Test
    fun `parse should return empty list for non-target emails`() {
        val sampleEmail =
            """
            MultipartContent(emailFrom=["Random Newsletter" <info@random.com>], emailSubject=Random Subject, emailReceivedDate=null, emailSentDate=2025-07-23T23:45:36, emailContentType=multipart/alternative; boundary="_----------=_MCPart_246769083", emailExtractedContent=Plain Text: Some random content.)
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertEquals(0, result.size, "Expected 0 articles for non-target email, but got ${result.size}")
    }

    private fun verifyArticle(
        article: MailContent,
        expectedTitle: String,
        expectedLink: String,
        expectedSection: String,
        expectedContentSnippet: String
    ) {
        assertNotNull(article, "Article with title containing '$expectedTitle' not found")
        assertEquals(expectedTitle, article.title)
        assertEquals(expectedLink, article.link)
        assertEquals(expectedSection, article.section)
        assertTrue(
            article.content.contains(expectedContentSnippet),
            "Article content should contain snippet '$expectedContentSnippet'"
        )
    }
}
