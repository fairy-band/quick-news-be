package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CooperpressWeeklyParserTest {
    private val parser = CooperpressWeeklyParser()

    @Test
    fun `supports 검증`() {
        assertTrue(parser.supports("peter@golangweekly.com", "Go experiments with SIMD"))
        assertTrue(parser.supports("frontend@cooperpress.com", "Frontend Focus #600"))
        assertTrue(parser.supports("react@cooperpress.com", "React Status #400"))
        assertTrue(parser.supports("jsw@peterc.org", "JavaScript Weekly #700"))
        assertTrue(parser.supports("rss@javascriptweekly.com", "JavaScript Weekly #701"))
    }

    @Test
    fun `Cooperpress 다중 섹션 파싱 테스트`() {
        val text = """
            # 567 — August 27, 2025
            
            * CONTAINER-AWARE GOMAXPROCS 
            ( https://go.dev/blog/container-aware-gomaxprocs )
            — The official Go blog kicks off a promised series of posts on Go 1.25’s new features with a look at some tweaked container-aware behavior around GOMAXPROCS.
              -- Michael Pratt
            
            * TOGETHER WITH SPONSOR 
            ( https://sponsor.example.com )
            — This is a sponsored ad that should be skipped.
            
            ⚡️ IN BRIEF:
            
            * GONZO: A GO-POWERED REALTIME LOG ANALYSIS TERMINAL 
            ( https://gonzo.controltheory.com/ )
            — A powerful, real-time log analysis terminal UI inspired by k9s.
            
            🛠 CODE, TOOLS & RELEASES:
            
            * WATERMILL 1.5: LIBRARY FOR BUILDING EVENT-DRIVEN APPS 
            ( https://watermill.io/ )
            — A library for working with message streams over Kafka and RabbitMQ.
            
            ------------------
            Curated by Peter Cooper
        """.trimIndent()

        val context = MailParseContext(
            content = text,
            subject = "Go experiments with SIMD",
            htmlContent = null,
        )

        val results = parser.parse(context)

        // Sponsor should be excluded, and articles across IN BRIEF and CODE, TOOLS must be extracted
        assertEquals(3, results.size)
        assertEquals("CONTAINER-AWARE GOMAXPROCS", results[0].title)
        assertEquals("GONZO: A GO-POWERED REALTIME LOG ANALYSIS TERMINAL", results[1].title)
        assertEquals("WATERMILL 1.5: LIBRARY FOR BUILDING EVENT-DRIVEN APPS", results[2].title)
    }
}
