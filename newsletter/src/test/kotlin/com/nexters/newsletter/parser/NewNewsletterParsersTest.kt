package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewNewsletterParsersTest {
    @Test
    fun `Maeil Mail parser should create one article from subject and body`() {
        val content =
            """
            오늘의 질문

            자바스크립트 이벤트 루프는 어떻게 동작하나요?

            이벤트 루프는 콜 스택과 태스크 큐를 조율해 비동기 콜백을 실행합니다.
            자세히 보기 https://www.maeil-mail.kr/question/123

            수신거부
            """.trimIndent()

        val result =
            MaeilMailParser().parse(
                content = content,
                subject = "[매일메일] 자바스크립트 이벤트 루프는 어떻게 동작하나요?",
                htmlContent = null,
            )

        assertEquals(1, result.size)
        assertEquals("자바스크립트 이벤트 루프는 어떻게 동작하나요?", result[0].title)
        assertEquals("https://www.maeil-mail.kr/question/123", result[0].link)
        assertEquals("Maeil Mail", result[0].section)
        assertTrue(result[0].content.contains("태스크 큐"))
    }

    @Test
    fun `Maeil Mail parser should prefer question link over template links`() {
        val html =
            """
            <html><body>
              <a href="https://www.maeil-mail.kr/">매일메일</a>
              <a href="https://www.maeil-mail.kr/question/137">답변 확인</a>
              <a href="https://www.maeil-mail.kr/question/mine/newsletter.feeding@gmail.com">내 질문</a>
              <a href="https://www.maeil-mail.kr/setting?email=newsletter.feeding@gmail.com&amp;token=token">설정</a>
              <a href="https://www.maeil-mail.kr/unsubscribe?email=newsletter.feeding@gmail.com&amp;token=token">수신거부</a>
            </body></html>
            """.trimIndent()

        val result =
            MaeilMailParser().parse(
                content = "",
                subject = "[매일메일] 시스템 간 비동기 연동 방식에는 무엇이 있나요?",
                htmlContent = html,
            )

        assertEquals(1, result.size)
        assertEquals("https://www.maeil-mail.kr/question/137", result[0].link)
    }

    @Test
    fun `TLDR parser should extract text digest links and skip sponsor items`() {
        val content =
            """
            TLDR

            Programming

            * Bun 2.0 Released (https://example.com/bun-2) - Bun 2.0 improves install speed and test runner compatibility for JavaScript projects.

            * Sponsored Cloud (https://example.com/sponsor) - SPONSORED: Run builds faster with a hosted cloud product.

            AI

            LLM Router Patterns (https://example.com/llm-router) - A practical guide to routing prompts across models in production systems.

            Unsubscribe
            """.trimIndent()

        val result = TLDRNewsletterParser().parse(content)

        assertEquals(2, result.size)
        assertEquals("Bun 2.0 Released", result[0].title)
        assertEquals("Programming", result[0].section)
        assertEquals("https://example.com/bun-2", result[0].link)
        assertEquals("LLM Router Patterns", result[1].title)
        assertTrue(result.none { it.title.contains("Sponsored") })
    }

    @Test
    fun `Baeldung parser should extract Baeldung article links`() {
        val content =
            """
            Java Weekly, Issue #612

            * Spring Boot Configuration Metadata (https://www.baeldung.com/spring-boot-configuration-metadata) - A focused guide to metadata generation in Spring Boot projects.

            * Learn Spring Security (https://www.baeldung.com/course) - course

            * Java Optional Patterns (https://www.baeldung.com/java-optional-patterns) - Patterns for using Optional without hiding domain errors.
            """.trimIndent()

        val result = BaeldungParser().parse(content)

        assertEquals(2, result.size)
        assertEquals("Spring Boot Configuration Metadata", result[0].title)
        assertTrue(result[0].content.contains("Issue #612"))
        assertEquals("https://www.baeldung.com/java-optional-patterns", result[1].link)
    }

    @Test
    fun `Yozm parser should extract magazine detail links from html`() {
        val html =
            """
            <html><body>
              <div>
                <a href="https://yozm.wishket.com/magazine/detail/3100/">AI 코드 리뷰 도구를 도입하기 전에 볼 것들</a>
                <p>AI 코드 리뷰 도구의 장점과 도입 전에 확인해야 할 운영 기준을 정리합니다.</p>
              </div>
              <a href="https://yozm.wishket.com/unsubscribe">수신거부</a>
            </body></html>
            """.trimIndent()

        val result = YozmParser().parse(content = "", subject = "요즘IT", htmlContent = html)

        assertEquals(1, result.size)
        assertEquals("AI 코드 리뷰 도구를 도입하기 전에 볼 것들", result[0].title)
        assertEquals("https://yozm.wishket.com/magazine/detail/3100/", result[0].link)
        assertEquals("Yozm", result[0].section)
        assertTrue(result[0].content.contains("운영 기준"))
    }

    @Test
    fun `Generic Substack parser should cover additional Substack senders`() {
        val content =
            """
            View this post on the web at https://weekly.fatbobman.com/p/swiftdata-predicates

            SwiftData predicate patterns and practical tradeoffs for iOS apps.

            Unsubscribe
            """.trimIndent()

        val result =
            GenericSubstackArticleParser().parse(
                content = content,
                subject = "SwiftData Predicate Patterns",
                htmlContent = null,
            )

        assertEquals(1, result.size)
        assertEquals("SwiftData Predicate Patterns", result[0].title)
        assertEquals("https://weekly.fatbobman.com/p/swiftdata-predicates", result[0].link)
        assertEquals("Fatbobman's Swift Weekly", result[0].section)
    }
}
