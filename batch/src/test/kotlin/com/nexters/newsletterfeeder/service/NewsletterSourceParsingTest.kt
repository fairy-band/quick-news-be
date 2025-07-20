package com.nexters.newsletterfeeder.service

import com.nexters.newsletterfeeder.dto.EmailMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class NewsletterSourceParsingTest {

    private lateinit var newsletterFormatter: NewsletterFormatter

    @BeforeEach
    fun setUp() {
        newsletterFormatter = NewsletterFormatter()
    }

    @Test
    fun `testBasicNewsletterParsing`() {
        val htmlContent = """
            <h1>Tech Newsletter</h1>
            <p>Welcome to our weekly tech newsletter!</p>
            
            <h2>AI Breakthrough</h2>
            <p>New developments in artificial intelligence...</p>
            
            <h2>Cloud Computing</h2>
            <p>Latest trends in cloud computing...</p>
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Tech Weekly <tech@example.com>"),
            emailSubject = "Weekly Tech Newsletter",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/html",
            emailExtractedContent = htmlContent,
            emailTextContent = null,
            emailHtmlContent = htmlContent,
            emailAttachments = emptyList(),
            content = htmlContent
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 기본 검증
        assertNotNull(formattedNewsletter, "포맷된 뉴스레터가 생성되어야 합니다")
        assertNotNull(formattedNewsletter.subject, "제목이 있어야 합니다")
    }

    @Test
    fun `testTextNewsletterParsing`() {
        val textContent = """
            Weekly Finance Report
            ====================
            
            Market Analysis
            ---------------
            The stock market showed strong performance this week...
            
            Economic Indicators
            -------------------
            Key economic indicators suggest...
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Finance Daily <finance@example.com>"),
            emailSubject = "Weekly Finance Report",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/plain",
            emailExtractedContent = textContent,
            emailTextContent = textContent,
            emailHtmlContent = null,
            emailAttachments = emptyList(),
            content = textContent
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 기본 검증
        assertNotNull(formattedNewsletter, "포맷된 뉴스레터가 생성되어야 합니다")
        assertNotNull(formattedNewsletter.subject, "제목이 있어야 합니다")
    }

    @Test
    fun `testNumberedNewsletterParsing`() {
        val textContent = """
            Daily News Summary
            
            1. Breaking News
            Major event occurred today...
            
            2. Technology Update
            Latest tech developments...
            
            3. Sports Highlights
            Today's sports news...
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Daily News <news@example.com>"),
            emailSubject = "Daily News Summary",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/plain",
            emailExtractedContent = textContent,
            emailTextContent = textContent,
            emailHtmlContent = null,
            emailAttachments = emptyList(),
            content = textContent
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 기본 검증
        assertNotNull(formattedNewsletter, "포맷된 뉴스레터가 생성되어야 합니다")
        assertNotNull(formattedNewsletter.subject, "제목이 있어야 합니다")
    }

    @Test
    fun `testSectionBasedNewsletterParsing`() {
        val htmlContent = """
            <div class="newsletter-header">
                <h1>Weekly Newsletter</h1>
            </div>
            
            <div class="article">
                <h2>First Article</h2>
                <p>This is the first article content...</p>
            </div>
            
            <div class="article">
                <h2>Second Article</h2>
                <p>This is the second article content...</p>
            </div>
            
            <div class="newsletter-footer">
                <p>Unsubscribe here</p>
            </div>
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Newsletter <newsletter@example.com>"),
            emailSubject = "Weekly Newsletter",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/html",
            emailExtractedContent = htmlContent,
            emailTextContent = null,
            emailHtmlContent = htmlContent,
            emailAttachments = emptyList(),
            content = htmlContent
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 기본 검증
        assertNotNull(formattedNewsletter, "포맷된 뉴스레터가 생성되어야 합니다")
        assertNotNull(formattedNewsletter.subject, "제목이 있어야 합니다")
    }

    @Test
    fun `testComplexNewsletterParsing`() {
        val complexHtml = """
            <html>
            <head>
                <title>Complex Newsletter</title>
                <style>body { font-family: Arial; }</style>
            </head>
            <body>
                <div class="header">
                    <h1>Advanced Tech Newsletter</h1>
                    <p>Your weekly dose of technology insights</p>
                </div>
                
                <div class="main-content">
                    <article>
                        <h2>Artificial Intelligence Trends</h2>
                        <p>Recent developments in AI show promising results...</p>
                        <ul>
                            <li>Machine Learning breakthroughs</li>
                            <li>Neural network improvements</li>
                            <li>AI ethics considerations</li>
                        </ul>
                    </article>
                    
                    <article>
                        <h2>Cloud Computing Updates</h2>
                        <p>Cloud platforms continue to evolve...</p>
                        <table>
                            <tr><th>Platform</th><th>New Features</th></tr>
                            <tr><td>AWS</td><td>Lambda improvements</td></tr>
                            <tr><td>Azure</td><td>AI integration</td></tr>
                        </table>
                    </article>
                </div>
                
                <div class="footer">
                    <p>Unsubscribe | Preferences | Contact Us</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Complex News <complex@example.com>"),
            emailSubject = "Complex Newsletter - Advanced Edition",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/html",
            emailExtractedContent = complexHtml,
            emailTextContent = null,
            emailHtmlContent = complexHtml,
            emailAttachments = emptyList(),
            content = complexHtml
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 기본 검증
        assertNotNull(formattedNewsletter, "포맷된 뉴스레터가 생성되어야 합니다")
        assertNotNull(formattedNewsletter.subject, "제목이 있어야 합니다")
    }

    @Test
    fun `testEncodingIssuesEmail`() {
        val contentWithEncodingIssues = """
            Newsletter with encoding issues
            ==============================
            
            Special characters: áéíóú ñ ç ß
            Emojis: 😀 🚀 💻
            Broken encoding: 
            
            This content has mixed encoding issues.
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Encoding Test <encoding@example.com>"),
            emailSubject = "Newsletter with Encoding Issues",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/plain",
            emailExtractedContent = contentWithEncodingIssues,
            emailTextContent = contentWithEncodingIssues,
            emailHtmlContent = null,
            emailAttachments = emptyList(),
            content = contentWithEncodingIssues
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 인코딩 문제가 있는 메일도 처리되어야 함
        assertNotNull(formattedNewsletter, "인코딩 문제가 있는 메일도 포맷된 뉴스레터가 생성되어야 합니다")
        assertTrue(formattedNewsletter.content.contains("Newsletter with encoding issues"), "제목이 포함되어야 합니다")
        
        // 인코딩 문제가 수정되었는지 확인
        assertFalse(formattedNewsletter.content.contains(""), "제어 문자가 제거되어야 합니다")
    }

    @Test
    fun `testMalformedEncodingEmail`() {
        val malformedContent = """
            Newsletter with malformed encoding
            =================================
            
            This content has malformed characters: 
            
            Some text with broken encoding.
        """.trimIndent()

        val emailMessage = EmailMessage.StringContent(
            emailFrom = listOf("Malformed Encoding <malformed@example.com>"),
            emailSubject = "Newsletter with Malformed Encoding",
            emailReceivedDate = LocalDateTime.now(),
            emailSentDate = LocalDateTime.now(),
            emailContentType = "text/plain",
            emailExtractedContent = malformedContent,
            emailTextContent = malformedContent,
            emailHtmlContent = null,
            emailAttachments = emptyList(),
            content = malformedContent
        )

        val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
        
        // 잘못된 인코딩도 처리되어야 함
        assertNotNull(formattedNewsletter, "잘못된 인코딩도 포맷된 뉴스레터가 생성되어야 합니다")
        assertTrue(formattedNewsletter.content.contains("Newsletter with malformed encoding"), "제목이 포함되어야 합니다")
    }
}
