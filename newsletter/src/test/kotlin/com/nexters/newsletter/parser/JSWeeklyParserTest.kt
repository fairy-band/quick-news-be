package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JSWeeklyParserTest {
    private val parser = JSWeeklyParser()

    @Test
    fun `isTarget should return true for JavaScript Weekly emails`() {
        assertTrue(parser.isTarget("JavaScript Weekly <jsw@peterc.org>"))
        assertTrue(parser.isTarget("jsw@peterc.org"))
        assertTrue(parser.isTarget("Javascript Weekly"))
    }

    @Test
    fun `isTarget should return false for non-JavaScript Weekly emails`() {
        assertFalse(parser.isTarget("Some Other Newsletter <info@example.com>"))
        assertFalse(parser.isTarget("random@example.com"))
    }

    @Test
    fun `parse should extract articles from JavaScript Weekly newsletter`() {
        val sampleEmail =
            """
            Plus did you know you can run JavaScript on MS-DOS? |

            #754 — September 26, 2025

            Read on the Web ( https://javascriptweekly.com/issues/754 )

            ------------------
            📖 ARTICLES AND VIDEOS
            ------------------

            * FROM STEAM TO FLOPPY: PORTING MODERN TYPESCRIPT TO RUN ON DOS
            ( https://jimb.ly/2025/09/23/qauntumpulse-from-steam-to-floppy/ )
            — The creator of a DOS-inspired programming game (available on
            Steam) wanted to try and get the game running on real DOS.
              -- Jimbly / Dashing Strike

            * NPM SECURITY BEST PRACTICES
            ( https://github.com/bodadotsh/npm-security-best-practices ) — An
            extensive list of best practices, techniques, and ideas to
            consider for making your use of the npm packaging ecosystem.
              -- Boda

            * JSON IS NOT JSON ACROSS LANGUAGES
            ( https://blog.dochia.dev/blog/json-isnt-json/ ) — If you use JSON
            to communicate between systems built in different languages,
            beware. Different libraries with varying opinions can cause issues.
              -- Dochia CLI

            ------------------
            🛠 CODE & TOOLS
            ------------------

            * GITHUB COPILOT CLI NOW IN PUBLIC PREVIEW
            ( https://github.blog/changelog/2025-09-25-github-copilot-cli-is-now-in-public-preview/ )
             — Not content to let Claude Code and OpenAI Codex dominate the
            CLI-based dev agent scene, GitHub has released a CLI-based
            version of Copilot, built using Node.
              -- GitHub

            * TANSTACK START V1 RELEASE CANDIDATE
            ( https://tanstack.com/blog/announcing-tanstack-start-v1 ) —
            TanStack&#39;s attempt at a full-stack TanStack Router-powered
            framework has reached a v1.0 release candidate.
              -- Tanner Linsley

            * CAP&#39;N WEB: A NEW RPC SYSTEM FOR BROWSERS AND WEB SERVERS
            ( https://blog.cloudflare.com/capnweb-javascript-rpc-library/ ) — A
            'spiritual sibling' to Cap&#39;n Proto, an RPC protocol created by
            one of the same authors.
              -- Varda and Faulkner (Cloudflare)

            📰 Classifieds

            Meticulous automatically creates and maintains an E2E UI test
            suite with zero developer effort.
            """.trimIndent()

        val result = parser.parse(sampleEmail)

        assertEquals(6, result.size, "Expected 6 articles, but got ${result.size}")

        // Articles section
        assertEquals("FROM STEAM TO FLOPPY: PORTING MODERN TYPESCRIPT TO RUN ON DOS", result[0].title)
        assertEquals("https://jimb.ly/2025/09/23/qauntumpulse-from-steam-to-floppy/", result[0].link)
        assertEquals("Articles", result[0].section)
        assertTrue(result[0].content.contains("Issue #754"))
        assertTrue(result[0].content.contains("September 26, 2025"))

        assertEquals("NPM SECURITY BEST PRACTICES", result[1].title)
        assertEquals("https://github.com/bodadotsh/npm-security-best-practices", result[1].link)
        assertEquals("Articles", result[1].section)

        assertEquals("JSON IS NOT JSON ACROSS LANGUAGES", result[2].title)
        assertEquals("https://blog.dochia.dev/blog/json-isnt-json/", result[2].link)
        assertEquals("Articles", result[2].section)

        // Tools section
        assertEquals("GITHUB COPILOT CLI NOW IN PUBLIC PREVIEW", result[3].title)
        assertEquals("https://github.blog/changelog/2025-09-25-github-copilot-cli-is-now-in-public-preview/", result[3].link)
        assertEquals("Tools", result[3].section)

        assertEquals("TANSTACK START V1 RELEASE CANDIDATE", result[4].title)
        assertEquals("https://tanstack.com/blog/announcing-tanstack-start-v1", result[4].link)
        assertEquals("Tools", result[4].section)
        assertTrue(result[4].content.contains("TanStack's attempt"))

        assertEquals("CAP'N WEB: A NEW RPC SYSTEM FOR BROWSERS AND WEB SERVERS", result[5].title)
        assertEquals("https://blog.cloudflare.com/capnweb-javascript-rpc-library/", result[5].link)
        assertEquals("Tools", result[5].section)
    }

    @Test
    fun `parse should handle HTML entities correctly`() {
        val emailContent =
            """
            #754 — September 26, 2025

            ------------------
            📖 ARTICLES AND VIDEOS
            ------------------

            * TANSTACK&#39;S NEW FRAMEWORK
            ( https://example.com/test ) — Testing &quot;quotes&quot; and &amp; symbols.
            """.trimIndent()

        val result = parser.parse(emailContent)

        assertEquals(1, result.size)
        assertEquals("TANSTACK'S NEW FRAMEWORK", result[0].title)
        assertTrue(result[0].content.contains("Testing \"quotes\" and & symbols"))
    }

    @Test
    fun `parse should filter out Classifieds section`() {
        val emailContent =
            """
            #754 — September 26, 2025

            ------------------
            📖 ARTICLES AND VIDEOS
            ------------------

            * VALID ARTICLE TITLE
            ( https://example.com/article ) — This is a valid article.

            📰 Classifieds

            * SPONSORED CONTENT
            ( https://example.com/sponsor ) — This should be filtered out.
            """.trimIndent()

        val result = parser.parse(emailContent)

        assertEquals(1, result.size)
        assertEquals("VALID ARTICLE TITLE", result[0].title)
    }

    @Test
    fun `parse should return empty list for empty content`() {
        val result = parser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse should skip titles shorter than 10 characters`() {
        val emailContent =
            """
            #754 — September 26, 2025

            ------------------
            📖 ARTICLES AND VIDEOS
            ------------------

            * SHORT
            ( https://example.com/short ) — This title is too short.

            * THIS IS A VALID LONG TITLE
            ( https://example.com/valid ) — This title is long enough.
            """.trimIndent()

        val result = parser.parse(emailContent)

        assertEquals(1, result.size)
        assertEquals("THIS IS A VALID LONG TITLE", result[0].title)
    }

    @Test
    fun `parse should handle sections without emojis`() {
        val emailContent =
            """
            #754 — September 26, 2025

            ------------------
            ARTICLES AND VIDEOS
            ------------------

            * ARTICLE WITHOUT EMOJI SECTION
            ( https://example.com/article ) — This should still be parsed.

            ------------------
            CODE & TOOLS
            ------------------

            * TOOL WITHOUT EMOJI SECTION
            ( https://example.com/tool ) — This should also be parsed.
            """.trimIndent()

        val result = parser.parse(emailContent)

        assertEquals(2, result.size)
        assertEquals("ARTICLE WITHOUT EMOJI SECTION", result[0].title)
        assertEquals("Articles", result[0].section)
        assertEquals("TOOL WITHOUT EMOJI SECTION", result[1].title)
        assertEquals("Tools", result[1].section)
    }

    @Test
    fun `parse should handle mixed emoji and non-emoji sections`() {
        val emailContent =
            """
            #754 — September 26, 2025

            📖 ARTICLES AND VIDEOS

            * ARTICLE WITH EMOJI
            ( https://example.com/article ) — Article section has emoji.

            CODE AND TOOLS

            * TOOL WITHOUT EMOJI
            ( https://example.com/tool ) — Tool section has no emoji.
            """.trimIndent()

        val result = parser.parse(emailContent)

        assertEquals(2, result.size)
        assertTrue(result.any { it.title == "ARTICLE WITH EMOJI" && it.section == "Articles" })
        assertTrue(result.any { it.title == "TOOL WITHOUT EMOJI" && it.section == "Tools" })
    }
}
