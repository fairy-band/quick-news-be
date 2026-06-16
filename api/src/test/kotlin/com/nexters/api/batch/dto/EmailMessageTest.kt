package com.nexters.api.batch.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EmailMessageTest {
    @Test
    fun `toString should not expose email body or headers`() {
        val message =
            EmailMessage.MultipartContent(
                emailFrom = listOf("Jacob's Tech Tavern <jacobbartlett@substack.com>"),
                emailSubject = "Touch to Pixels: UI Pipeline Internals on iOS",
                emailReceivedDate = null,
                emailSentDate = LocalDateTime.of(2026, 5, 14, 0, 3, 1),
                emailContentType = "multipart/alternative; boundary=\"secret-boundary\"",
                emailExtractedContent = "Plain Text: I will save you 20 mins if you do not want to read a whole blog post",
                emailTextContent = "I will save you 20 mins if you do not want to read a whole blog post",
                emailHtmlContent = "<html><body>private html body</body></html>",
                emailAttachments =
                    listOf(
                        AttachmentInfo(
                            fileName = "private.txt",
                            contentType = "text/plain",
                            size = 11,
                            data = "secret-data".toByteArray(),
                        ),
                    ),
                emailHeaders =
                    mapOf(
                        "Message-ID" to "private-message-id",
                        "Authentication-Results" to "private-auth-header",
                    ),
            )

        val result = message.toString()

        assertThat(result).contains("MultipartContent")
        assertThat(result).contains("fromDomains=[substack.com]")
        assertThat(result).contains("subject=Touch to Pixels: UI Pipeline Internals on iOS")
        assertThat(result).contains("extractedContentLength=")
        assertThat(result).contains("textContentLength=")
        assertThat(result).contains("htmlContentLength=")
        assertThat(result).contains("attachmentCount=1")
        assertThat(result).contains("headerCount=2")
        assertThat(result).doesNotContain("I will save you 20 mins")
        assertThat(result).doesNotContain("private html body")
        assertThat(result).doesNotContain("secret-data")
        assertThat(result).doesNotContain("private-message-id")
        assertThat(result).doesNotContain("private-auth-header")
    }

    @Test
    fun `attachment toString should not expose byte array contents`() {
        val attachment =
            AttachmentInfo(
                fileName = "private.txt",
                contentType = "text/plain",
                size = 11,
                data = "secret-data".toByteArray(),
            )

        val result = attachment.toString()

        assertThat(result).contains("dataSize=11")
        assertThat(result).doesNotContain("secret-data")
    }
}
