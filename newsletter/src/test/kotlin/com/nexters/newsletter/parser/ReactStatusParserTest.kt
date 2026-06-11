package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactStatusParserTest {
    private val parser = ReactStatusParser()

    @Test
    fun `React Status 뉴스레터 파싱 테스트`() {
        // Given - 실제 React Status 이메일 내용
        val emailContent =
            """
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

        // Then - 전체 파싱 결과 검증
        assertEquals(7, result.size, "총 7개 콘텐츠가 파싱되어야 함")

        // === Main 섹션 검증 ===
        assertEquals("THE HISTORY OF REACT THROUGH CODE", result[0].title)
        assertEquals("https://playfulprogramming.com/posts/react-history-through-code", result[0].link)
        assertEquals("Main", result[0].section)
        assertTrue(result[0].content.contains("Issue #"))

        assertEquals("ANNOUNCING NODE-API SUPPORT FOR REACT NATIVE", result[1].title)
        assertEquals("https://www.callstack.com/blog/announcing-node-api-support-for-react-native", result[1].link)
        assertEquals("Main", result[1].section)
        assertTrue(result[1].content.contains("Issue #"))

        // === Brief 섹션 검증 ===
        assertEquals("🐝 Wasp", result[2].title)
        assertEquals("https://wasp.sh/", result[2].link)
        assertEquals("Brief", result[2].section)

        assertEquals("Next.js 15.4 Released", result[3].title)
        assertEquals("https://nextjs.org/blog/next-15-4", result[3].link)
        assertEquals("Brief", result[3].section)

        // === Tool 섹션은 라이브러리 묶음 콘텐츠로 검증 ===
        assertEquals("2025년 29주의 라이브러리", result[4].title)
        assertEquals("https://react.statuscode.com", result[4].link)
        assertEquals("Libraries", result[4].section)
        assertTrue(result[4].content.contains("REACT-EASY-CROP: A COMPONENT FOR INTERACTIVE IMAGE CROPPING"))
        assertTrue(result[4].content.contains("REACTPLAYER 3.2: A COMPONENT FOR PLAYING MEDIA FROM URLS"))

        // === Elsewhere 섹션 검증 ===
        assertTrue(result[5].title.contains("New versions"))
        assertEquals("https://react.statuscode.com", result[5].link)
        assertEquals("Elsewhere", result[5].section)

        assertTrue(result[6].title.contains("JavaScript's Date class"))
        assertEquals("https://react.statuscode.com", result[6].link)
        assertEquals("Elsewhere", result[6].section)
    }

    @Test
    fun `발신자 정보 파싱 테스트`() {
        assertTrue(parser.isTarget("React Status <react@cooperpress.com>"))
        assertTrue(parser.isTarget("react@cooperpress.com"))
        assertTrue(parser.isTarget("REACT STATUS"))
        assertFalse(parser.isTarget("other@newsletter.com"))
    }

    @Test
    fun `URL 추출 테스트`() {
        // Given
        val emailContent =
            """
            * TEST ARTICLE TITLE
            ( https://example.com/test-article )
            — This is a test article description
            """.trimIndent()

        // When
        val result = parser.parse(emailContent)

        // Then
        assertEquals(1, result.size)
        assertEquals("TEST ARTICLE TITLE", result[0].title)
        assertEquals("https://example.com/test-article", result[0].link)
        assertEquals("Main", result[0].section)
    }

    @Test
    fun `빈 이메일 내용 처리 테스트`() {
        // Given
        val emptyContent = ""

        // When
        val result = parser.parse(emptyContent)

        // Then
        assertTrue(result.isEmpty())
    }
}
