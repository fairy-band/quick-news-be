package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JavaScriptWeeklyParserTest {
    private val sut = JavaScriptWeeklyParser()

    @Test
    fun `isTarget should return true for JavaScript Weekly emails`() {
        assertTrue(sut.isTarget("JavaScript Weekly <jsw@peterc.org>"))
        assertTrue(sut.isTarget("jsw@peterc.org"))
        assertTrue(sut.isTarget("javascriptweekly.com newsletter"))
    }

    @Test
    fun `isTarget should return false for non-JavaScript Weekly emails`() {
        assertFalse(sut.isTarget("Bytes <noreply@ui.dev>"))
        assertFalse(sut.isTarget("Donny Wals <donny@donnywals.com>"))
        assertFalse(sut.isTarget("random@example.com"))
    }

    @Test
    fun `parse should extract articles from JavaScript Weekly newsletter`() {
        val sampleEmail =
            """
            Content-Type: text/plain; charset="utf-8"

            #745 — July 18, 2025

            * THE JAVASCRIPT DATE QUIZ ( https://jsdate.wtf/ ) — Prepare to get irritated? JavaScript's native date parsing features are notoriously arcane and prone to cause surprises.
              -- Sam Rose

            * NEXT.JS 15.4 RELEASED AND WHAT'S COMING IN NEXT.JS 16 ( https://nextjs.org/blog/next-15-4 ) — A relatively small release for Next, but with updates to performance, stability, and Turbopack compatibility.
              -- Jimmy Lai and Zack Tanner

            * ADD SSO & SCIM WITH JUST A FEW LINES OF CODE ( https://workos.com/?utm_source=javascript&utm_medium=newsletter ) — WorkOS offers clean, well-documented APIs for SSO, SCIM, RBAC, and more.
              -- WorkOS (SPONSOR)

            IN BRIEF:

            * Vue 3.6 Alpha has been released ( https://github.com/vuejs/core/releases/tag/v3.6.0-alpha.1 ) as a preview of what's coming up. Vapor Mode is a key addition for compiling single file components.

            * React Native is gaining support for Node-API ( https://www.callstack.com/blog/announcing-node-api-support-for-react-native ), opening up a lot of possibilities for code-sharing.

            * The Node.js team is discussing whether to move Node to annual major releases ( https://github.com/nodejs/Release/issues/1113 ) (as opposed to twice a year now).

            📖 ARTICLES AND VIDEOS

            * HOW TO CREATE AN NPM PACKAGE IN 2025 ( https://www.totaltypescript.com/how-to-create-an-npm-package ) — One of JavaScript's most essential tasks, but one with numerous steps involved if you want to follow best practices.
              -- Matt Pocock

            * THE HISTORY OF REACT THROUGH CODE ( https://playfulprogramming.com/posts/react-history-through-code ) — An epic article charting React's evolution from its origins at Facebook through to now.
              -- Corbin Crutchley

            🛠 CODE & TOOLS

            * TIPTAP V3: THE HEADLESS RICH TEXT EDITOR ( https://tiptap.dev/ ) — A toolkit for building rich text editors. Headless, framework-agnostic and extendable.

            * NEUTRALINOJS 6.2 ( https://github.com/neutralinojs/neutralinojs/releases/tag/v6.2.0 ) — A lightweight alternative to Electron for building desktop apps.

            RELEASES:

            * Nuxt v4.0 ( https://nuxt.com/blog/v4 ) – A major DX-focused release for the popular full-stack Vue.js framework.

            * ESLint v9.31.0 ( https://eslint.org/blog/2025/07/eslint-v9.31.0-released/ ) – Four core rules have been updated to support explicit resource management.
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertTrue(result.isNotEmpty(), "Should parse some content")

        // Verify Featured articles (main section)
        val featuredArticles = result.filter { it.section == "Featured" }
        assertTrue(featuredArticles.size >= 2, "Should have at least 2 featured articles")

        verifyArticle(
            featuredArticles.find { it.title.contains("JAVASCRIPT DATE QUIZ") },
            "THE JAVASCRIPT DATE QUIZ",
            "https://jsdate.wtf/",
            "Featured"
        )

        verifyArticle(
            featuredArticles.find { it.title.contains("NEXT.JS 15.4") },
            "NEXT.JS 15.4 RELEASED AND WHAT'S COMING IN NEXT.JS 16",
            "https://nextjs.org/blog/next-15-4",
            "Featured"
        )

        // Verify In Brief articles
        val inBriefArticles = result.filter { it.section == "In Brief" }
        assertTrue(inBriefArticles.size >= 3, "Should have at least 3 In Brief articles")

        verifyArticle(
            inBriefArticles.find { it.title.contains("Vue 3.6") },
            "Vue 3.6 Alpha has been released",
            "https://github.com/vuejs/core/releases/tag/v3.6.0-alpha.1",
            "In Brief"
        )

        // Verify Articles & Videos section
        val articlesVideosResults = result.filter { it.section == "Articles & Videos" }
        assertTrue(articlesVideosResults.size >= 2, "Should have Articles & Videos content")

        verifyArticle(
            articlesVideosResults.find { it.title.contains("NPM PACKAGE") },
            "HOW TO CREATE AN NPM PACKAGE IN 2025",
            "https://www.totaltypescript.com/how-to-create-an-npm-package",
            "Articles & Videos"
        )

        // Verify Code & Tools section
        val codeToolsResults = result.filter { it.section == "Code & Tools" }
        assertTrue(codeToolsResults.size >= 2, "Should have Code & Tools content")

        verifyArticle(
            codeToolsResults.find { it.title.contains("TIPTAP") },
            "TIPTAP V3: THE HEADLESS RICH TEXT EDITOR",
            "https://tiptap.dev/",
            "Code & Tools"
        )

        // Verify Releases section
        val releasesResults = result.filter { it.section == "Releases" }
        assertTrue(releasesResults.size >= 2, "Should have Releases content")

        verifyArticle(
            releasesResults.find { it.title.contains("Nuxt") },
            "Nuxt v4.0",
            "https://nuxt.com/blog/v4",
            "Releases"
        )

        // Verify sponsor content is filtered out
        val sponsorArticles =
            result.filter {
                it.title.contains("sponsor", ignoreCase = true) ||
                    it.title.contains("WorkOS", ignoreCase = true) ||
                    it.content.contains("SPONSOR", ignoreCase = true)
            }
        assertTrue(sponsorArticles.isEmpty(), "Should filter out sponsored content")

        // Verify issue info is included
        result.forEach { article ->
            assertTrue(article.content.contains("Issue #745"), "Content should contain issue number")
            assertTrue(article.content.contains("July 18, 2025"), "Content should contain issue date")
        }
    }

    @Test
    fun `parse should handle content without clear sections`() {
        val contentWithoutSections =
            """
            #745 — July 18, 2025

            * Some random article ( https://example.com/article )
            * Another article without proper section ( https://example.com/article2 )
            """.trimIndent()

        // when
        val result = sut.parse(contentWithoutSections)

        // then
        assertNotNull(result, "Should handle content without clear sections")
    }

    @Test
    fun `parse should extract issue information correctly`() {
        val contentWithIssue = "#745 — July 18, 2025\n\n* Some article ( https://example.com )"

        // when
        val result = sut.parse(contentWithIssue)

        // then
        result.forEach { article ->
            assertTrue(
                article.content.contains("Issue #745"),
                "Should contain correct issue number: ${article.content}"
            )
            assertTrue(
                article.content.contains("July 18, 2025"),
                "Should contain correct date: ${article.content}"
            )
        }
    }

    @Test
    fun `parse should filter out sponsored content effectively`() {
        val contentWithSponsors =
            """
            * GOOD ARTICLE ( https://example.com/good ) — Real content about JavaScript
            * SPONSORED CONTENT ( https://sponsor.com/ad ) — This is sponsored content
            * ANOTHER SPONSOR ( https://example.com/sponsor ) — Advertisement here
            """.trimIndent()

        // when
        val result = sut.parse(contentWithSponsors)

        // then
        result.forEach { article ->
            assertFalse(
                article.title.contains("sponsor", ignoreCase = true) ||
                    article.title.contains("advertisement", ignoreCase = true) ||
                    article.title.contains("sponsored", ignoreCase = true),
                "Should filter out sponsored content: ${article.title}"
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
