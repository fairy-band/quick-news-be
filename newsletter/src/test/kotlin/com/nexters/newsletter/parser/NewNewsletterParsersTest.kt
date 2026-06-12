package com.nexters.newsletter.parser

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.entity.NewsletterSourceEnrichment
import com.nexters.external.entity.WebPageEnrichment
import com.nexters.external.entity.WebPageEnrichmentItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NewNewsletterParsersTest {
    @Test
    fun `Maeil Mail parser should skip raw template without enrichment`() {
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
                MailParseContext(
                    content = content,
                    subject = "[매일메일] 자바스크립트 이벤트 루프는 어떻게 동작하나요?",
                    htmlContent = null,
                ),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `Maeil Mail parser should create contents from successful enrichment only`() {
        val result =
            MaeilMailParser().parse(
                MailParseContext(
                    content = "오늘의 질문이 도착했습니다.",
                    subject = "[매일메일] 시스템 간 비동기 연동 방식에는 무엇이 있나요?",
                    htmlContent = null,
                    webPageEnrichment =
                        MailWebPageEnrichment(
                            items =
                                listOf(
                                    MailWebPageEnrichmentItem(
                                        url = "https://www.maeil-mail.kr/question/137",
                                        normalizedUrl = "https://www.maeil-mail.kr/question/137",
                                        title = "[매일메일] 시스템 간 비동기 연동 방식에는 무엇이 있나요?",
                                        content = "분리된 시스템 간의 비동기 연동은 결합도를 낮출 수 있습니다.",
                                        imageUrl = "https://example.com/image.png",
                                        status = "success",
                                    ),
                                    MailWebPageEnrichmentItem(
                                        url = "https://www.maeil-mail.kr/question/138",
                                        title = "실패한 보강",
                                        content = "저장되면 안 됩니다.",
                                        status = "failed",
                                    ),
                                ),
                        ),
                ),
            )

        assertEquals(1, result.size)
        assertEquals("시스템 간 비동기 연동 방식에는 무엇이 있나요?", result[0].title)
        assertEquals("https://www.maeil-mail.kr/question/137", result[0].link)
        assertEquals("분리된 시스템 간의 비동기 연동은 결합도를 낮출 수 있습니다.", result[0].content)
        assertEquals("https://example.com/image.png", result[0].imageUrl)
        assertEquals("Maeil Mail", result[0].section)
    }

    @Test
    fun `MailParseContext should include web page enrichment from newsletter source`() {
        val source =
            NewsletterSource(
                subject = "[매일메일] 시스템 간 비동기 연동 방식에는 무엇이 있나요?",
                sender = "매일메일",
                senderEmail = "noreply@maeil-mail.kr",
                recipient = "newsletter.feeding@gmail.com",
                recipientEmail = "newsletter.feeding@gmail.com",
                content = "오늘의 질문이 도착했습니다.",
                contentType = "text/plain",
                receivedDate = LocalDateTime.parse("2026-06-12T09:00:00"),
                enrichment =
                    NewsletterSourceEnrichment(
                        webPage =
                            WebPageEnrichment(
                                items =
                                    listOf(
                                        WebPageEnrichmentItem(
                                            url = "https://www.maeil-mail.kr/question/137",
                                            normalizedUrl = "https://www.maeil-mail.kr/question/137",
                                            title = "시스템 간 비동기 연동 방식에는 무엇이 있나요?",
                                            content = "분리된 시스템 간의 비동기 연동은 결합도를 낮출 수 있습니다.",
                                            imageUrl = "https://example.com/image.png",
                                            status = "success",
                                        ),
                                    ),
                            ),
                    ),
            )

        val context = MailParseContext.from(source)
        val result = MaeilMailParser().parse(context)

        assertEquals(1, context.webPageEnrichment.items.size)
        assertEquals("https://www.maeil-mail.kr/question/137", context.webPageEnrichment.items[0].url)
        assertEquals("분리된 시스템 간의 비동기 연동은 결합도를 낮출 수 있습니다.", result[0].content)
        assertEquals("https://example.com/image.png", result[0].imageUrl)
    }

    @Test
    fun `Maeil Mail parser should use subject when enrichment title is missing`() {
        val result =
            MaeilMailParser().parse(
                MailParseContext(
                    content = "",
                    subject = "[매일메일] 리액트에서 컴포넌트란 무엇인가요?",
                    htmlContent = null,
                    webPageEnrichment =
                        MailWebPageEnrichment(
                            items =
                                listOf(
                                    MailWebPageEnrichmentItem(
                                        url = "https://www.maeil-mail.kr/question/94",
                                        content = "리액트에서 컴포넌트는 UI를 구성하는 독립적인 단위입니다.",
                                        status = "success",
                                    ),
                                ),
                        ),
                ),
            )

        assertEquals(1, result.size)
        assertEquals("리액트에서 컴포넌트란 무엇인가요?", result[0].title)
        assertEquals("https://www.maeil-mail.kr/question/94", result[0].link)
    }

    @Test
    fun `Maeil Mail parser should deduplicate enrichment URLs`() {
        val result =
            MaeilMailParser().parse(
                MailParseContext(
                    content = "",
                    subject = "[매일메일] 리액트에서 컴포넌트란 무엇인가요?",
                    htmlContent = null,
                    webPageEnrichment =
                        MailWebPageEnrichment(
                            items =
                                listOf(
                                    MailWebPageEnrichmentItem(
                                        url = "https://www.maeil-mail.kr/question/94?utm_source=email",
                                        normalizedUrl = "https://www.maeil-mail.kr/question/94",
                                        title = "리액트에서 컴포넌트란 무엇인가요?",
                                        content = "첫 번째 본문",
                                        status = "success",
                                    ),
                                    MailWebPageEnrichmentItem(
                                        url = "https://www.maeil-mail.kr/question/94",
                                        normalizedUrl = "https://www.maeil-mail.kr/question/94",
                                        title = "리액트에서 컴포넌트란 무엇인가요?",
                                        content = "두 번째 본문",
                                        status = "success",
                                    ),
                                ),
                        ),
                ),
            )

        assertEquals(1, result.size)
        assertEquals("첫 번째 본문", result[0].content)
    }

    @Test
    fun `web page enrichment should resolve exact URL before normalized fallback`() {
        val enrichment =
            MailWebPageEnrichment(
                items =
                    listOf(
                        MailWebPageEnrichmentItem(
                            url = "https://example.com/article?utm_source=newsletter",
                            normalizedUrl = "https://example.com/article",
                            content = "tracked url content",
                            status = "success",
                        ),
                        MailWebPageEnrichmentItem(
                            url = "https://example.com/article",
                            normalizedUrl = "https://example.com/article",
                            content = "exact url content",
                            status = "success",
                        ),
                    ),
            )

        val result = enrichment.findSuccessfulContentItem("https://example.com/article")

        assertEquals("exact url content", result?.content)
    }

    @Test
    fun `web page enrichment should resolve enrichment key before URL fallback`() {
        val enrichment =
            MailWebPageEnrichment(
                items =
                    listOf(
                        MailWebPageEnrichmentItem(
                            url = "https://example.com/first",
                            normalizedUrl = "https://example.com/first",
                            content = "first content",
                            status = "success",
                        ),
                        MailWebPageEnrichmentItem(
                            url = "https://example.com/second",
                            normalizedUrl = "https://example.com/second",
                            content = "second content",
                            status = "success",
                        ),
                    ),
            )

        val secondItem = enrichment.items[1]
        val result =
            enrichment.findSuccessfulContentItem(
                url = "https://example.com/first",
                enrichmentKey = secondItem.enrichmentKey,
            )

        assertEquals("second content", result?.content)
    }

    @Test
    fun `web page enrichment key should ignore surrounding URL whitespace`() {
        val item =
            MailWebPageEnrichmentItem(
                url = " https://example.com/article ",
                normalizedUrl = " https://example.com/article ",
                content = "content",
                status = "success",
            )
        val enrichment = MailWebPageEnrichment(items = listOf(item))

        val result =
            enrichment.findSuccessfulContentItem(
                url = "https://example.com/fallback",
                enrichmentKey = "https://example.com/article#https://example.com/article",
            )

        assertEquals("content", result?.content)
    }

    @Test
    fun `web page enrichment should resolve item by normalized URL`() {
        val enrichment =
            MailWebPageEnrichment(
                items =
                    listOf(
                        MailWebPageEnrichmentItem(
                            url = "https://example.com/article?utm_source=newsletter#section",
                            content = "normalized url content",
                            status = "success",
                        ),
                    ),
            )

        val result = enrichment.findSuccessfulContentItem("https://example.com/article")

        assertEquals("normalized url content", result?.content)
    }

    @Test
    fun `Maeil Mail parser should not parse template links without enrichment`() {
        val html =
            """
            <html><body>
              <a href="https://www.maeil-mail.kr/">매일메일</a>
              <a href="https://www.maeil-mail.kr/question/137">답변 확인</a>
              <a href="https://www.maeil-mail.kr/unsubscribe?email=newsletter.feeding@gmail.com&amp;token=token">수신거부</a>
            </body></html>
            """.trimIndent()

        val result =
            MaeilMailParser().parse(
                MailParseContext(
                    content = "",
                    subject = "[매일메일] 시스템 간 비동기 연동 방식에는 무엇이 있나요?",
                    htmlContent = html,
                ),
            )

        assertTrue(result.isEmpty())
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
