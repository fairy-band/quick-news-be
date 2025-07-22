package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HtmlTextExtractorTest {
    private val sut = HtmlTextExtractor

    @Test
    fun `extractText should remove HTML tags and decode entities`() {
        val htmlContent = """
            <html>
                <body>
                    <h1>제목입니다</h1>
                    <p>이것은 <strong>강조된</strong> 텍스트입니다.</p>
                    <div>&amp; 앰퍼샌드 &lt; 미만 &gt; 초과 &nbsp; 공백</div>
                </body>
            </html>
        """.trimIndent()

        // when
        val result = sut.extractText(htmlContent)

        // then
        assertTrue(result.contains("제목입니다"), "Should contain text content")
        assertTrue(result.contains("강조된"), "Should contain text content")
        assertTrue(result.contains("&"), "Should decode &amp;")
        assertTrue(result.contains("<"), "Should decode &lt;")
        assertTrue(result.contains(">"), "Should decode &gt;")
    }

    @Test
    fun `extractLinks should return valid HTTP links only`() {
        val htmlWithLinks = """
            <p><a href="https://example.com">Example Link</a></p>
            <p><a href="https://github.com/user/repo" target="_blank">GitHub Repository</a></p>
            <p><a href="mailto:test@example.com">Email Link</a></p>
            <p><a href="javascript:void(0)">JavaScript Link</a></p>
        """.trimIndent()

        // when
        val links = sut.extractLinks(htmlWithLinks)

        // then
        assertEquals(2, links.size, "Should extract only HTTP links")

        val (title1, url1) = links[0]
        assertEquals("Example Link", title1)
        assertEquals("https://example.com", url1)

        val (title2, url2) = links[1]
        assertEquals("GitHub Repository", title2)
        assertEquals("https://github.com/user/repo", url2)
    }

    @Test
    fun `extractHeadings should return headings with correct levels`() {
        val htmlWithHeadings = """
            <h1>메인 제목</h1>
            <h2>서브 제목</h2>
            <h3>작은 제목</h3>
        """.trimIndent()

        // when
        val headings = sut.extractHeadings(htmlWithHeadings)

        // then
        assertEquals(3, headings.size)
        assertEquals(1 to "메인 제목", headings[0])
        assertEquals(2 to "서브 제목", headings[1])
        assertEquals(3 to "작은 제목", headings[2])
    }

    @Test
    fun `extractListItems should return all list items`() {
        val htmlWithLists = """
            <ul>
                <li>첫 번째 아이템</li>
                <li>두 번째 <strong>아이템</strong></li>
                <li>세 번째 아이템 <a href="https://example.com">링크 포함</a></li>
            </ul>
            <ol>
                <li>순서 있는 첫 번째</li>
            </ol>
        """.trimIndent()

        // when
        val listItems = sut.extractListItems(htmlWithLists)

        // then
        assertEquals(4, listItems.size)
        assertTrue(listItems.contains("첫 번째 아이템"))
        assertTrue(listItems.contains("두 번째 아이템"))
        assertTrue(listItems.any { it.contains("세 번째 아이템") && it.contains("링크 포함") })
        assertTrue(listItems.contains("순서 있는 첫 번째"))
    }

    @Test
    fun `extractByTag should return content of specified tags`() {
        val htmlWithDivs = """
            <div>첫 번째 div</div>
            <p>단락입니다</p>
            <div>두 번째 <span>div</span></div>
        """.trimIndent()

        // when
        val divTexts = sut.extractByTag(htmlWithDivs, "div")
        val pTexts = sut.extractByTag(htmlWithDivs, "p")

        // then
        assertEquals(2, divTexts.size)
        assertTrue(divTexts.contains("첫 번째 div"))
        assertTrue(divTexts.contains("두 번째 div"))

        assertEquals(1, pTexts.size)
        assertTrue(pTexts.contains("단락입니다"))
    }

    @Test
    fun `isHtml should detect HTML content correctly`() {
        val testCases = listOf(
            "<!DOCTYPE html><html><body>HTML입니다</body></html>" to true,
            "<body>Some content</body>" to true,
            "<html>Content</html>" to true,
            "이것은 일반 텍스트입니다" to false,
            "일부 <strong>HTML</strong> 태그가 있습니다" to false
        )

        testCases.forEach { (content, expected) ->
            // when & then
            assertEquals(
                expected,
                sut.isHtml(content),
                "HTML detection failed for: $content"
            )
        }
    }

    @Test
    fun `extractText should remove script and style tags completely`() {
        val htmlWithScriptAndStyle = """
            <html>
                <head>
                    <style>
                        body { background: red; }
                        .class { display: none; }
                    </style>
                    <script>
                        console.log("This should be removed");
                        alert("Remove this too");
                    </script>
                </head>
                <body>
                    <p>실제 콘텐츠입니다</p>
                    <script>document.write("Bad script");</script>
                </body>
            </html>
        """.trimIndent()

        // when
        val result = sut.extractText(htmlWithScriptAndStyle)

        // then
        assertFalse(result.contains("background: red"), "Should remove CSS")
        assertFalse(result.contains("console.log"), "Should remove JavaScript")
        assertFalse(result.contains("alert"), "Should remove JavaScript")
        assertFalse(result.contains("document.write"), "Should remove inline JavaScript")
        assertTrue(result.contains("실제 콘텐츠입니다"), "Should keep actual content")
    }

    @Test
    fun `extractText should handle empty content gracefully`() {
        val emptyContents = listOf("", "   ", "<html></html>", "<p></p>", "<div></div>")

        emptyContents.forEach { content ->
            // when
            val result = sut.extractText(content)

            // then
            assertTrue(
                result.isEmpty(),
                "Empty content should return empty string, but got: '$result'"
            )
        }
    }

    @Test
    fun `extractText should normalize whitespace properly`() {
        val messyHtml = """
            <p>여러    공백이     있는    텍스트</p>
            <div>

            연속된
            줄바꿈이

            있는 텍스트
            </div>
        """.trimIndent()

        // when
        val result = sut.extractText(messyHtml)

        // then
        assertFalse(result.contains("    "), "Should normalize multiple spaces")
        // 연속된 줄바꿈은 허용하되, 과도한 것만 제한
        assertTrue(result.contains("여러 공백이 있는 텍스트"), "Should contain normalized text")
    }
}
