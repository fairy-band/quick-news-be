package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactStatusParserTest {
    private val parser = ReactStatusParser()

    @Test
    fun `React Status 뉴스레터 파싱 테스트`() {
        // Given - 실제 React Status 이메일 내용 (로그에서 확인한 내용)
        val emailContent = """
            Plus an epic tour through the history of React. |

            #​436 — July 16, 2025

            ------------------

            ⚛️ REACT STATUS

            ------------------

            * THE HISTORY OF REACT THROUGH CODE
            ( https://playfulprogramming.com/posts/react-history-through-code )
            — An epic attempt charting React's evolution from its origins at
            Facebook to what we know and use today. It sheds light on React's
            core philosophies, the motivations behind major API and feature
            decisions, and how it solved real problems developers were
            facing.

            * ANNOUNCING NODE-API SUPPORT FOR REACT NATIVE
            ( https://www.callstack.com/blog/announcing-node-api-support-for-react-native )
             — A huge development for React Native. By bringing Node's
            native module system into React Native, many doors are opened for
            code-sharing between platforms, prebuilding native modules for
            faster build times.

            IN BRIEF:

            * 🐝 Wasp ( https://wasp.sh/ ) is a popular Ruby on Rails-like
            framework for React, Node.js and Prisma, and it now has a public
            development roadmap.

            * Next.js 15.4 Released ( https://nextjs.org/blog/next-15-4 ) — A relatively small release
            for Next, but with updates to performance, stability, and
            Turbopack compatibility.

            ------------------
            🛠 CODE, TOOLS & LIBRARIES
            ------------------

            * REACT-EASY-CROP: A COMPONENT FOR INTERACTIVE IMAGE CROPPING
            ( https://valentinh.github.io/react-easy-crop/ ) — Supports any
            image format (and even videos) along with dragging, zooming, and
            rotations.

            * REACTPLAYER 3.2: A COMPONENT FOR PLAYING MEDIA FROM URLS
            ( https://github.com/cookpete/react-player ) — As well as standard
            videos, it can play HLS streams, DASH streams, YouTube videos,
            Vimeo videos, and more.

            ------------------
            📢 ELSEWHERE IN JAVASCRIPT
            ------------------

            * New versions of all maintained Node.js release lines have just
            been released including Node v20.19.4, v22.17.1, and v24.4.1.

            * How well do you know JavaScript's Date class and how date
            parsing works in JavaScript? Find out with this quiz.
            """.trimIndent()

        // When
        val result = parser.parse(emailContent)

        // Then
        assertTrue(result.isNotEmpty())

        // 메인 기사들이 파싱되었는지 확인
        val mainArticles = result.filter { it.section == "Main" }
        assertTrue(mainArticles.isNotEmpty())

        // 특정 기사가 파싱되었는지 확인
        val historyArticle = result.find { it.title.contains("THE HISTORY OF REACT THROUGH CODE") }
        assertTrue(historyArticle != null)
        assertTrue(historyArticle!!.link.contains("playfulprogramming.com"))

        val nodeApiArticle = result.find { it.title.contains("ANNOUNCING NODE-API SUPPORT") }
        assertTrue(nodeApiArticle != null)
        assertTrue(nodeApiArticle!!.link.contains("callstack.com"))

        // IN BRIEF 섹션이 파싱되었는지 확인
        val briefItems = result.filter { it.section == "Brief" }
        assertTrue(briefItems.isNotEmpty())

        // TOOLS 섹션이 파싱되었는지 확인
        val toolItems = result.filter { it.section == "Tool" }
        assertTrue(toolItems.isNotEmpty())

        // ELSEWHERE 섹션이 파싱되었는지 확인
        val elsewhereItems = result.filter { it.section == "Elsewhere" }
        assertTrue(elsewhereItems.isNotEmpty())
    }

    @Test
    fun `isTarget 동작 테스트`() {
        assertTrue(parser.isTarget("React Status <react@cooperpress.com>"))
        assertTrue(parser.isTarget("react@cooperpress.com"))
        assertTrue(parser.isTarget("REACT STATUS"))
        assertTrue(!parser.isTarget("other@newsletter.com"))
    }

    @Test
    fun `URL 추출 테스트`() {
        // Given
        val emailContent = """
            * TEST ARTICLE TITLE
            ( https://example.com/test-article )
            — This is a test article description
            """.trimIndent()

        // When
        val result = parser.parse(emailContent)

        // Then
        val article = result.find { it.title.contains("TEST ARTICLE TITLE") }
        assertTrue(article != null)
        assertEquals("https://example.com/test-article", article!!.link)
    }
}
