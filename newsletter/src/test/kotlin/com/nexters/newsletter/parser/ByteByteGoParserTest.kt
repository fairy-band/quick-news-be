package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteByteGoParserTest {
    private val parser = ByteByteGoParser()

    @Test
    fun `supports 검증`() {
        assertTrue(parser.supports("bytebytego@mail.beehiiv.com", "PostgreSQL versus MySQL"))
        assertTrue(parser.supports("Alex Xu from ByteByteGo", "CPU vs GPU vs TPU"))
        assertTrue(parser.supports("someone@test.com", "ByteByteGo Newsletter #100"))
    }

    @Test
    fun `ByteByteGo HTML 파싱 테스트`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>CPU vs GPU vs TPU</title></head>
            <body>
            <a href="https://link.mail.beehiiv.com/ss/c/web-version">Read Online</a>
            
            <h2>✂️ Cut your QA cycles down to minutes with QA Wolf (Sponsored)</h2>
            <p>Automate your end-to-end testing with QA Wolf.</p>
            
            <h2>CPU vs GPU vs TPU</h2>
            <div>
              <img src="https://media.beehiiv.com/cdn-cgi/image/fit=scale-down/cpu-gpu-tpu.png" alt="CPU vs GPU vs TPU architecture" />
              <p>CPUs, GPUs, and TPUs serve fundamentally different computing paradigms in modern infrastructure. CPUs excel at sequential processing and general-purpose logic with low latency.</p>
              <p>GPUs are optimized for massive parallel throughput across thousands of cores, making them ideal for matrix operations and deep learning training.</p>
            </div>
            
            <h2>How OAuth 2 Works</h2>
            <div>
              <img src="https://media.beehiiv.com/cdn-cgi/image/fit=scale-down/oauth2.png" alt="OAuth2 Flow" />
              <p>OAuth 2.0 is the industry-standard protocol for authorization. It enables third-party applications to obtain limited access to user accounts on an HTTP service.</p>
            </div>

            <h2>Become an AI Engineer | Cohort-Based Course</h2>
            <p>Enroll now before spots fill up.</p>

            <h3>How would you rate today's newsletter?</h3>
            <p>Give us your feedback.</p>
            </body>
            </html>
        """.trimIndent()

        val context = MailParseContext(
            content = html,
            subject = "CPU vs GPU vs TPU",
            htmlContent = html,
        )

        val results = parser.parse(context)

        // Sponsored, Course promo, and rating must be excluded
        assertEquals(2, results.size)

        assertEquals("CPU vs GPU vs TPU", results[0].title)
        assertTrue(results[0].content.contains("sequential processing"))
        assertEquals("https://media.beehiiv.com/cdn-cgi/image/fit=scale-down/cpu-gpu-tpu.png", results[0].imageUrl)

        assertEquals("How OAuth 2 Works", results[1].title)
        assertTrue(results[1].content.contains("authorization"))
        assertEquals("https://media.beehiiv.com/cdn-cgi/image/fit=scale-down/oauth2.png", results[1].imageUrl)
    }
}
