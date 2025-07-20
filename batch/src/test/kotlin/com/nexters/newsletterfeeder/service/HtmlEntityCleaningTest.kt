package com.nexters.newsletterfeeder.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HtmlEntityCleaningTest {

    private val newsletterFormatter = NewsletterFormatter()

    @Test
    fun `testHtmlEntityCleaning`() {
        val dirtyContent = """
            This is a test content with HTML entities:
            &#8199; &#173;&#847; &#8199; &#173;&#847; &#8199; &#173;&#847;
            &nbsp; &lt; &gt; &amp; &quot; &#39;
            Some normal text here.
        """.trimIndent()

        val cleanedContent = newsletterFormatter.cleanHtmlContentForNewsletter(dirtyContent)
        
        // HTML 엔티티가 제거되었는지 확인
        assertEquals(false, cleanedContent.contains("&#8199;"))
        assertEquals(false, cleanedContent.contains("&#173;"))
        assertEquals(false, cleanedContent.contains("&#847;"))
        assertEquals(false, cleanedContent.contains("&nbsp;"))
        assertEquals(false, cleanedContent.contains("&lt;"))
        assertEquals(false, cleanedContent.contains("&gt;"))
        assertEquals(false, cleanedContent.contains("&amp;"))
        assertEquals(false, cleanedContent.contains("&quot;"))
        assertEquals(false, cleanedContent.contains("&#39;"))
        
        // 정상 텍스트는 유지되는지 확인
        assertEquals(true, cleanedContent.contains("This is a test content"))
        assertEquals(true, cleanedContent.contains("Some normal text here"))
    }
} 