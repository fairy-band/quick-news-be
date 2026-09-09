package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmashingMagazineParserTest {
    private val parser = SmashingMagazineParser()

    @Test
    fun `supports 검증`() {
        assertTrue(parser.supports("newsletter@smashingmagazine.com", "#518: Useful Figma Plugins and Tools"))
        assertTrue(parser.supports("Smashing Magazine Team", "Issue 519: AI Patterns"))
        assertTrue(parser.supports("someone@test.com", "Smashing Newsletter"))
    }

    @Test
    fun `Smashing Magazine HTML 파싱 테스트`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>#520: CSS and SVG</title></head>
            <body>
            <a href="https://smashingmagazine.com/issues/520">Read online</a>
            
            <h1>Halò Smashing Friends,</h1>
            <p>Welcome to our latest edition full of frontend gems.</p>
            
            <h2>1. Scroll-Spy Effect</h2>
            <div>
              <img src="https://mcusercontent.com/16b832d9ad4b28edf261f34df/images/scroll-spy.png" alt="Scroll-Spy Preview" />
              <p>Creating a robust scroll-spy effect without relying on heavy third-party JavaScript libraries is now straightforward using modern CSS and IntersectionObserver.</p>
              <a href="https://smashingmagazine.us1.list-manage.com/track/click?url=scroll-spy">Read article</a>
            </div>
            
            <h2>2. What You Need To Know About SVG</h2>
            <div>
              <p>Scalable Vector Graphics are a fundamental building block of modern web interfaces, but mastering viewBox and path syntax requires understanding its core coordinate space.</p>
              <a href="https://smashingmagazine.us1.list-manage.com/track/click?url=svg-deep-dive">Explore SVG</a>
            </div>

            <h2>3. Upcoming Workshops and Conferences</h2>
            <p>Join our practical workshops on CSS and web design.</p>
            </body>
            </html>
        """.trimIndent()

        val context = MailParseContext(
            content = html,
            subject = "#520: CSS and SVG",
            htmlContent = html,
        )

        val results = parser.parse(context)

        // Upcoming Workshops should be skipped
        assertEquals(2, results.size)

        assertEquals("Scroll-Spy Effect", results[0].title)
        assertTrue(results[0].content.contains("IntersectionObserver"))
        assertEquals("https://mcusercontent.com/16b832d9ad4b28edf261f34df/images/scroll-spy.png", results[0].imageUrl)

        assertEquals("What You Need To Know About SVG", results[1].title)
        assertTrue(results[1].content.contains("Scalable Vector Graphics"))
    }
}
