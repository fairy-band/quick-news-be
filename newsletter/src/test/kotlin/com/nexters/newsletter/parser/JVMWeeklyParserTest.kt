package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JVMWeeklyParserTest {
    private val parser = JVMWeeklyParser()

    @Test
    fun `supports JVM Weekly senders and subjects`() {
        assertTrue(parser.supports("vived@substack.com", "September: The Rest of the Story - JVM Weekly vol. 146"))
        assertTrue(parser.supports("Artur Skowronski from JVM Weekly", "JVM Weekly vol. 147"))
        assertTrue(parser.supports("someone@test.com", "JVM Weekly vol. 148"))
    }

    @Test
    fun `extracts linked cards from a JVM Weekly email`() {
        val html =
            """
            <!DOCTYPE html>
            <html><body>
            <h1>September: The Rest of the Story - JVM Weekly vol. 146</h1>
            <a href="https://open.substack.com/pub/vived/p/september-the-rest-of-the-story-jvm">READ IN APP</a>
            <h2>1. Missed in September</h2>
            <p>Introductory text for the section that should be skipped as a section header.</p>
            <h2>2. Release Radar</h2>
            <h3><a href="https://quarkus.io/blog/quarkus-3-26-0-released/">Quarkus 3.26</a></h3>
            <p>Quarkus 3.26 has been released with a range of important updates. The most significant include the update to SmallRye OpenAPI and reactive messaging enhancements.</p>
            <h3>Lombok 1.18.40</h3>
            <p>I rarely write about such minor versions, but Lombok 1.18.40 is a key update for many projects. It fixes compatibility with Java 25 and modern compiler features. Find out more <a href="https://projectlombok.org/changelog">on the changelog</a>.</p>
            <h2>3. GitHub All-Stars</h2>
            <h3><a href="https://github.com/test/jarinker">Jarinker</a></h3>
            <p>Jarinker, created by Daniel Liu, is a tool for dependency analysis and building smaller, optimized Java runtime images.</p>
            <h3>Subscribe to our newsletter</h3>
            <p>Join thousands of Java developers.</p>
            </body></html>
            """.trimIndent()

        val results = parser.parse(MailParseContext(content = html, subject = "JVM Weekly vol. 146", htmlContent = html))

        assertEquals(3, results.size)
        assertEquals("Quarkus 3.26", results[0].title)
        assertEquals("https://quarkus.io/blog/quarkus-3-26-0-released/", results[0].link)
        assertEquals("https://projectlombok.org/changelog", results[1].link)
        assertEquals("https://github.com/test/jarinker", results[2].link)
    }

    @Test
    fun `keeps a canonical JVM Weekly article as one content despite internal headings`() {
        val canonicalUrl = "https://open.substack.com/pub/vived/p/understanding-mcp-through-raw-stdio"
        val externalUrl = "https://example.com/quarkus-release"
        val email =
            """
            <html><body>
              <a href="$canonicalUrl">Read the MCP article</a>
              <h2>Understanding MCP Through Raw STDIO Communication</h2>
              <p>This article introduces the protocol and its implementation details in Java.</p>
              <h3>Why STDIO? The Power of Universal Communication</h3>
              <p>STDIO keeps the transport portable and easy to inspect in local development.</p>
              <h3>Understanding the JSON-RPC Message Flow</h3>
              <p>The client and server exchange typed JSON-RPC messages over standard input and output.</p>
              <h2><a href="$externalUrl">Quarkus 3.26</a></h2>
              <p>The release adds practical improvements for production Java applications.</p>
            </body></html>
            """.trimIndent()

        val result = parser.parse(email, subject = "JVM Weekly")

        assertEquals(2, result.size)
        assertEquals(canonicalUrl, result[0].link)
        assertEquals(externalUrl, result[1].link)
        assertTrue(result.none { it.title == "Why STDIO? The Power of Universal Communication" })
    }
}
