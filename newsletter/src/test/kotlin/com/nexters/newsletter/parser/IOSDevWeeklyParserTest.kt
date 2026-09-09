package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IOSDevWeeklyParserTest {
    private val parser = IOSDevWeeklyParser()

    @Test
    fun `supports 검증`() {
        assertTrue(parser.supports("dave@iosdevweekly.com", "iOS Dev Weekly – Issue 748"))
        assertTrue(parser.supports("newsletter@iosdevweekly.com", "iOS Dev Weekly – Issue 752"))
        assertTrue(parser.supports("Dave Verwer", "Issue 750"))
    }

    @Test
    fun `iOS Dev Weekly HTML 파싱 테스트`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>iOS Dev Weekly Issue 719</title></head>
            <body>
            <table role="heading"><tbody><tr><td>News</td></tr></tbody></table>
            <a href="https://developer.apple.com/news/?id=ks7">Updated age ratings in App Store Connect</a>
            <p>New age rating categories are coming with this year’s operating system updates.</p>
            
            <table role="heading"><tbody><tr><td>Tools</td></tr></tbody></table>
            <a href="https://github.com/sahilsatralkar/iOSImageOptimizer">iOS Image Optimizer</a>
            <p>This tool from Sahil Satralkar looks interesting. Its primary task is to find unused images.</p>

            <table role="heading"><tbody><tr><td>Code</td></tr></tbody></table>
            <a href="https://www.swiftbysundell.com/articles/deciding-between-let-and-var/">Deciding between ‘let’ and ‘var’ for Swift struct properties</a>
            <p>You might think you know the answer to the question John Sundell asks in this blog post title.</p>
            
            <table role="heading"><tbody><tr><td>Jobs</td></tr></tbody></table>
            <a href="https://example.com/job">Senior iOS Engineer at Tech Co</a>
            <p>We are hiring engineers.</p>
            
            <table role="heading"><tbody><tr><td>Sponsors</td></tr></tbody></table>
            <a href="https://example.com/sponsor">Try awesome tool today</a>
            <p>Special offer for subscribers.</p>
            </body>
            </html>
        """.trimIndent()

        val context = MailParseContext(
            content = html,
            subject = "iOS Dev Weekly – Issue 719",
            htmlContent = html,
        )

        val results = parser.parse(context)

        // Jobs and Sponsors should be filtered out
        assertEquals(3, results.size)
        
        assertEquals("Updated age ratings in App Store Connect", results[0].title)
        assertEquals("https://developer.apple.com/news/?id=ks7", results[0].link)
        assertEquals("News", results[0].section)
        
        assertEquals("iOS Image Optimizer", results[1].title)
        assertEquals("https://github.com/sahilsatralkar/iOSImageOptimizer", results[1].link)
        assertEquals("Tools", results[1].section)
        
        assertEquals("Deciding between ‘let’ and ‘var’ for Swift struct properties", results[2].title)
        assertEquals("https://www.swiftbysundell.com/articles/deciding-between-let-and-var/", results[2].link)
        assertEquals("Code", results[2].section)
    }
}
