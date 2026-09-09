package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MicroservicesIOParserTest {
    private val parser = MicroservicesIOParser()

    @Test
    fun `supports 검증`() {
        assertTrue(parser.supports("chris@chrisrichardson.net", "Microservices.IO: Microservices rules #9"))
        assertTrue(parser.supports("someone@test.com", "Reminder: Microservices.IO: Developing observable services"))
    }

    @Test
    fun `MicroservicesIO HTML 파싱 테스트`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Microservices Rules</title></head>
            <body>
            <h1>Microservices rules #9: Develop observable services</h1>
            <p>Observability is a critical quality attribute for microservices architectures. A microservice must be designed from the ground up to expose health checks, metrics, and distributed tracing context.</p>
            <p>By implementing proactive telemetry with OpenTelemetry standards, teams can rapidly detect and isolate failures across complex dependency graphs.</p>
            <p>You are receiving this email because you subscribed to microservices.io.</p>
            <a href="https://microservices.io/patterns/observability/distributed-tracing.html">Read Pattern</a>
            </body>
            </html>
        """.trimIndent()

        val context = MailParseContext(
            content = html,
            subject = "Reminder: Microservices.IO: Microservices rules #9: Develop observable services",
            htmlContent = html,
        )

        val results = parser.parse(context)

        assertEquals(1, results.size)
        assertEquals("Microservices rules #9: Develop observable services", results[0].title)
        assertTrue(results[0].content.contains("OpenTelemetry"))
        assertEquals("https://microservices.io/patterns/observability/distributed-tracing.html", results[0].link)
    }
}
