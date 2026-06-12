package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatestNewsletterParsersTest {
    @Test
    fun `factory should register latest information newsletter parsers`() {
        val factory = MailParserFactory()

        assertNewsletterParser(MaeilMailParser::class.java, factory, "noreply@maeil-mail.kr")
        assertNewsletterParser(TLDRNewsletterParser::class.java, factory, "dan@tldrnewsletter.com")
        assertNewsletterParser(BaeldungParser::class.java, factory, "eugen@baeldung.com")
        assertNewsletterParser(YozmParser::class.java, factory, "yozm_help@wishket.com")
        assertNewsletterParser(BytesDevParser::class.java, factory, "tyler@ui.dev")
        assertNewsletterParser(KoreanFeArticleParser::class.java, factory, "kofearticle@substack.com")
        assertNewsletterParser(CooperpressWeeklyParser::class.java, factory, "node@cooperpress.com")
        assertNewsletterParser(PythonWeeklyParser::class.java, factory, "rahul@pythonweekly.com")
        assertNewsletterParser(AndroidWeeklyParser::class.java, factory, "contact@androidweekly.net")
        assertNewsletterParser(
            ItWorldKoreaParser::class.java,
            factory,
            "itworld@techlibrary.co.kr",
            "[ITWorld 뉴스레터] 보안 전략",
        )
        assertNewsletterParser(WebToolsWeeklyParser::class.java, factory, "submissions@webtoolsweekly.com")
        assertNewsletterParser(VSCodeEmailParser::class.java, factory, "submissions@vscode.email")
        assertNewsletterParser(GenericSubstackArticleParser::class.java, factory, "pragmaticengineer@substack.com")
        assertNewsletterParser(GenericSubstackArticleParser::class.java, factory, "architectureweekly@substack.com")
        assertNewsletterParser(GenericSubstackArticleParser::class.java, factory, "fatbobman@substack.com")
        assertNewsletterParser(GenericSubstackArticleParser::class.java, factory, "jacobbartlett@substack.com")
        assertNewsletterParser(ReactStatusParser::class.java, factory, "react@cooperpress.com")
        assertNewsletterParser(IlbunParser::class.java, factory, "morning@ilbuntok.com")
        assertNewsletterParser(LibHuntWeeklyParser::class.java, factory, "Awesome Kotlin Weekly <newsletter@libhunt.com>")
    }

    @Test
    fun `factory should filter processable newsletters by subject when sender is shared`() {
        val factory = MailParserFactory()

        assertInstanceOf(
            ItWorldKoreaParser::class.java,
            factory.findParser("itworld@techlibrary.co.kr", "[ITWorld 뉴스레터] 보안 전략"),
        )
        assertNull(factory.findParser("itworld@techlibrary.co.kr", "(광고) 웨비나 초대"))
        assertNull(factory.findParser("submissions@webtoolsweekly.com", "Web Tools Weekly: Subscription Confirmed"))
        assertInstanceOf(ReactStatusParser::class.java, factory.findParser("react@cooperpress.com", "React issue"))
    }

    private fun assertNewsletterParser(
        expectedType: Class<out MailParser>,
        factory: MailParserFactory,
        sender: String,
        subject: String = "Newsletter issue",
    ) {
        assertInstanceOf(expectedType, factory.findParser(sender, subject))
    }

    @Test
    fun `Cooperpress parser should extract article blocks and skip sponsors`() {
        val content =
            """
            #​627 — June 4, 2026 — Read on the Web

            Node.js Weekly

            * REPLACEMENTS.FYI: FIND REPLACEMENTS FOR NPM PACKAGES

              ( https://replacements.fyi/ )

            Type in a package name and get suggestions of lighter alternatives.

              -- e18e

            * MEMETRIA K/V: EFFICIENT REDIS & VALKEY HOSTING

              ( https://dashboard.memetria.com/nodeweekly/ )

            Memetria K/V hosts Redis OSS and Valkey for Node.js apps.

              -- Memetria (SPONSOR)
            """.trimIndent()

        val result = CooperpressWeeklyParser().parse(content)

        assertEquals(1, result.size)
        assertEquals("REPLACEMENTS.FYI: FIND REPLACEMENTS FOR NPM PACKAGES", result[0].title)
        assertEquals("https://replacements.fyi/", result[0].link)
        assertTrue(result[0].content.contains("Issue #627"))
    }

    @Test
    fun `Bytes parser should extract the main thing`() {
        val content =
            """
            Today’s issue: frontend tooling news.

            Welcome to #493 ( https://bytes.dev/archives/493 ).

            --------------

            The Main Thing

            --------------

            Watching VC-backed open source reach the endgame

            The VC-backed Open Source Endgame

            ---------------------------------

            Cloudflare announced that they are acquiring VoidZero
            ( https://blog.cloudflare.com/voidzero-joins-cloudflare ).

            Cool Bits
            """.trimIndent()

        val result = BytesDevParser().parse(content, "Bytes: The VC-backed Open Source Endgame", null)

        assertEquals(1, result.size)
        assertEquals("The VC-backed Open Source Endgame", result[0].title)
        assertEquals("https://blog.cloudflare.com/voidzero-joins-cloudflare", result[0].link)
        assertTrue(result[0].content.contains("Issue #493"))
    }

    @Test
    fun `Korean FE Article parser should use subject as title`() {
        val content =
            """
            View this post on the web at https://kofearticle.substack.com/p/example

            글 링크: https://example.com/typescript-performance [ https://substack.com/redirect/example ]

            소개

            대규모 타입스크립트 모노레포에서 타입 검사 성능을 추적해 해결한 사례를 소개합니다.

            팀원들의 의견

            의견 내용
            """.trimIndent()

        val result =
            KoreanFeArticleParser().parse(
                content = content,
                subject = "[Korean FE Article] 타입스크립트 성능 문제 해결하기",
                htmlContent = null,
            )

        assertEquals(1, result.size)
        assertEquals("타입스크립트 성능 문제 해결하기", result[0].title)
        assertEquals("https://example.com/typescript-performance", result[0].link)
        assertTrue(result[0].content.contains("타입 검사 성능"))
    }

    @Test
    fun `Python Weekly parser should extract markdown article links`() {
        val content =
            """
            Python Weekly - Issue 747

            ##### **Articles, Tutorials and Talks**

            ###### [Opaque Types in Python](https://blog.glyph.im/2026/05/opaque-types-in-python.html)

            A proposed technique for exposing an opaque data structure with idiomatic modern Python.

            ###### [Build a Live Object Detection App](https://example.com/object-detection)

            Learn to build and ship a live object detection app.
            """.trimIndent()

        val result = PythonWeeklyParser().parse(content)

        assertEquals(2, result.size)
        assertEquals("Opaque Types in Python", result[0].title)
        assertEquals("Articles, Tutorials and Talks", result[0].section)
        assertTrue(result[0].content.contains("Issue #747"))
    }

    @Test
    fun `Android Weekly parser should extract article cards from html`() {
        val html =
            """
            <html><body>
              <div>
                <img src="https://androidweekly.net/images/adaptive-nav.png" width="320" height="180" />
                <div>
                  <a href="https://example.com/adaptive-nav">AdaptiveNavBar: A Compose Multiplatform Library</a>
                </div>
                <div>A Compose Multiplatform library that renders platform-native navigation from one shared API.</div>
              </div>
              <div>
                <div>
                  <a href="https://example.com/sponsor">Why do mobile releases land on the EM?</a>
                </div>
                <div>Sponsored report for engineering managers.</div>
              </div>
            </body></html>
            """.trimIndent()

        val result = AndroidWeeklyParser().parse("", "Android Weekly #730", html)

        assertEquals(1, result.size)
        assertEquals("AdaptiveNavBar: A Compose Multiplatform Library", result[0].title)
        assertEquals("https://example.com/adaptive-nav", result[0].link)
        assertEquals("https://androidweekly.net/images/adaptive-nav.png", result[0].imageUrl)
    }

    @Test
    fun `ITWorld parser should extract newsletter slots and decode original url`() {
        val html =
            """
            <html><body>
              <div>
                <img src="/images/agent.png" width="320" height="180" />
                <a linklabel="Slot One Title" href="http://abc.techlibrary.co.kr/newsletter_detect.php?url=https%3A%2F%2Fwww.itworld.co.kr%2Farticle%2F4181485">
                  왜 AI 에이전트는 프로덕션에서 실망스러울까
                </a>
                <a linklabel="Slot One Description" href="http://abc.techlibrary.co.kr/newsletter_detect.php?url=https%3A%2F%2Fwww.itworld.co.kr%2Farticle%2F4181485">
                  신뢰할 수 있는 에이전트를 원한다면 하부 구조에 네 가지 보장을 구현해야 한다.
                </a>
              </div>
            </body></html>
            """.trimIndent()

        val result = ItWorldKoreaParser().parse("", "[ITWorld 뉴스레터] 왜 AI 에이전트는 프로덕션에서 실망스러울까", html)

        assertEquals(1, result.size)
        assertEquals("왜 AI 에이전트는 프로덕션에서 실망스러울까", result[0].title)
        assertEquals("https://www.itworld.co.kr/article/4181485", result[0].link)
        assertEquals("https://www.itworld.co.kr/images/agent.png", result[0].imageUrl)
    }

    @Test
    fun `ITWorld parser should fall back to content when htmlContent has no article markers`() {
        val content =
            """
            <html><body>
              <div>
                <a linklabel="Slot One Title" href="http://abc.techlibrary.co.kr/newsletter_detect.php?url=https%3A%2F%2Fwww.itworld.co.kr%2Farticle%2F5000">
                  AI 운영 전략을 다시 세우는 방법
                </a>
                <a linklabel="Slot One Description" href="http://abc.techlibrary.co.kr/newsletter_detect.php?url=https%3A%2F%2Fwww.itworld.co.kr%2Farticle%2F5000">
                  조직의 AI 운영 방식을 점검하고 안정적인 확장 전략을 세우는 방법을 소개한다.
                </a>
              </div>
            </body></html>
            """.trimIndent()

        val result =
            ItWorldKoreaParser().parse(
                content = content,
                subject = "[ITWorld 뉴스레터] AI 운영 전략",
                htmlContent = "<html><body>footer only</body></html>",
            )

        assertEquals(1, result.size)
        assertEquals("AI 운영 전략을 다시 세우는 방법", result[0].title)
        assertEquals("https://www.itworld.co.kr/article/5000", result[0].link)
    }

    @Test
    fun `Web Tools Weekly parser should extract numbered tool links and skip sponsors`() {
        val content =
            """
            Issue #672 • June 4, 2026

            CSS & HTML TOOLS

            FONT CHANGER PRO [3] — Type any text and this tool will generate
            fancy text in 1000+ styles.

            HUBSPOT DEVELOPER PLATFORM [4] — Ship faster with AI coding tools. SPONSORED

            AI TOOLS, LLMS, ETC.

            OPEN AGENTS [5] — A reference app for building and running
            background coding agents on Vercel.

            Links:
            ------
            [3] https://example.com/font-changer
            [4] https://example.com/sponsor
            [5] https://example.com/open-agents
            """.trimIndent()

        val result = WebToolsWeeklyParser().parse(content)

        assertEquals(1, result.size)
        assertEquals("2026년 23주의 라이브러리", result[0].title)
        assertEquals("Libraries", result[0].section)
        assertEquals("https://example.com/font-changer", result[0].link)
        assertTrue(result[0].content.contains("Issue #672"))
        assertTrue(result[0].content.contains("FONT CHANGER PRO"))
        assertTrue(result[0].content.contains("OPEN AGENTS"))
        assertTrue(!result[0].content.contains("HUBSPOT DEVELOPER PLATFORM"))
    }

    @Test
    fun `Web Tools Weekly parser should extract legacy inline tool links`() {
        val content =
            """
            Plain Text: Web Tools Weekly
            Issue #633 • September 4, 2025

            ** CSS & HTML Tools
            ------------------------------------------------------------

            CSS Properties (https://example.com/css-properties) — A complete reference of CSS properties.

            CodeRabbit (https://example.com/sponsor) — Cut code review time and bugs in half. SPONSORED

            ** AI Tools, LLMs, etc.
            ------------------------------------------------------------

            Open Agents (https://example.com/open-agents) — A reference app for building and running background coding agents.

            HTML: <html></html>
            """.trimIndent()

        val result = WebToolsWeeklyParser().parse(content)

        assertEquals(1, result.size)
        assertEquals("2025년 36주의 라이브러리", result[0].title)
        assertEquals("https://example.com/css-properties", result[0].link)
        assertTrue(result[0].content.contains("CSS Properties"))
        assertTrue(result[0].content.contains("Open Agents"))
        assertTrue(!result[0].content.contains("CodeRabbit"))
    }

    @Test
    fun `VSCode Email parser should extract wrapped titles`() {
        val content =
            """
            Issue 214 • May 27, 2026

            VS CODE ARTICLES & VIDEOS

            KEEP YOUR CONTEXT SHORT - MANUAL COMPACTION IS NOW AVAILABLE IN VS
            CODE [12] — A look at a new VS Code feature for keeping agent
            context concise.

            BEST OF THE REST

            MONOSKETCH [14] — A powerful ASCII sketching and diagramming app.

            Links:
            ------
            [12] https://example.com/compaction
            [14] https://example.com/monosketch
            """.trimIndent()

        val result = VSCodeEmailParser().parse(content)

        assertEquals(2, result.size)
        assertEquals("KEEP YOUR CONTEXT SHORT - MANUAL COMPACTION IS NOW AVAILABLE IN VS CODE", result[0].title)
        assertEquals("https://example.com/compaction", result[0].link)
    }

    @Test
    fun `VSCode Email parser should extract legacy inline links`() {
        val content =
            """
            Plain Text: VSCode.Email
            Issue 176 • September 3, 2025

            ** VS Code Tools
            ------------------------------------------------------------
            Codex (https://example.com/codex) — The official coding agent extension.

            Sponsor Tool (https://example.com/sponsor) — Sponsored placement. Sponsor

            ** VS Code Articles & Videos
            ------------------------------------------------------------
            MCP Servers in VS Code (https://example.com/mcp) — Video from the VS Code team.

            HTML: <html></html>
            """.trimIndent()

        val result = VSCodeEmailParser().parse(content)

        assertEquals(2, result.size)
        assertEquals("Codex", result[0].title)
        assertEquals("https://example.com/codex", result[0].link)
        assertEquals("MCP Servers in VS Code", result[1].title)
        assertTrue(!result.any { it.title == "Sponsor Tool" })
    }

    @Test
    fun `Generic Substack parser should extract a single article and remove sponsor block`() {
        val content =
            """
            View this post on the web at https://newsletter.pragmaticengineer.com/p/opencode

            Stream the latest episode
            Listen and watch now on YouTube [ https://substack.com/redirect/youtube ].
            Brought to You by
            Sponsor copy that should not become the summary.
            In this episode
            We meet Dax Raad, co-founder of OpenCode, for a discussion about the gaps in developer tooling.

            Unsubscribe https://substack.com/redirect/unsubscribe
            """.trimIndent()

        val result =
            GenericSubstackArticleParser().parse(
                content = content,
                subject = "Building OpenCode with Dax Raad",
                htmlContent = null,
            )

        assertEquals(1, result.size)
        assertEquals("Building OpenCode with Dax Raad", result[0].title)
        assertEquals("https://newsletter.pragmaticengineer.com/p/opencode", result[0].link)
        assertEquals("The Pragmatic Engineer", result[0].section)
        assertTrue(result[0].content.contains("OpenCode"))
        assertTrue(!result[0].content.contains("Sponsor copy"))
    }

    @Test
    fun `Generic Substack parser should fallback Architecture Weekly adhoc email to publication link`() {
        val html =
            """
            <html><body>
              <h1 class="post-title">NPM unsafe and vulnerable for supply chain. Well, not quite...</h1>
              <div class="body markup"><img src="https://example.com/npm-supply-chain.png" width="640" height="360" /></div>
              <a href="https://substack.com/@oskardudycz">Oskar Dudycz</a>
            </body></html>
            """.trimIndent()

        val result =
            GenericSubstackArticleParser().parse(
                content = "Welcome to the new week!\nNPM trusted publishing helps reduce common package release risks.",
                subject = null,
                htmlContent = html,
            )

        assertEquals(1, result.size)
        assertEquals("NPM unsafe and vulnerable for supply chain. Well, not quite...", result[0].title)
        assertEquals("https://www.architecture-weekly.com", result[0].link)
        assertEquals("Architecture Weekly", result[0].section)
        assertEquals("https://example.com/npm-supply-chain.png", result[0].imageUrl)
    }
}
