package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JVMWeeklyParserTest {
    private val parser = JVMWeeklyParser()

    @Test
    fun `supports 검증`() {
        assertTrue(parser.supports("vived@substack.com", "September: The Rest of the Story - JVM Weekly vol. 146"))
        assertTrue(parser.supports("Artur Skowronski from JVM Weekly", "JVM Weekly vol. 147"))
        assertTrue(parser.supports("someone@test.com", "JVM Weekly vol. 148"))
    }

    @Test
    fun `JVM Weekly HTML 파싱 테스트`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>JVM Weekly vol. 146</title></head>
            <body>
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
            </body>
            </html>
        """.trimIndent()

        val context = MailParseContext(
            content = html,
            subject = "September: The Rest of the Story - JVM Weekly vol. 146",
            htmlContent = html,
        )

        val results = parser.parse(context)

        // Missed in September, Release Radar, GitHub All-Stars and Subscribe should be skipped
        assertEquals(3, results.size)

        assertEquals("Quarkus 3.26", results[0].title)
        assertEquals("https://quarkus.io/blog/quarkus-3-26-0-released/", results[0].link)
        assertEquals("JVM Weekly", results[0].section)
        assertTrue(results[0].content.contains("Quarkus 3.26 has been released"))

        assertEquals("Lombok 1.18.40", results[1].title)
        assertEquals("https://projectlombok.org/changelog", results[1].link)

        assertEquals("Jarinker", results[2].title)
        assertEquals("https://github.com/test/jarinker", results[2].link)
    }
}
