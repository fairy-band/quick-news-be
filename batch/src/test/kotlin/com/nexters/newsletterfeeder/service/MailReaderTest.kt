package com.nexters.newsletterfeeder.service

import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.springframework.integration.core.MessageSource
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import java.util.Date
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MailReaderTest {
    @Test
    fun `should successfully read and process email message when new email exists`() {
        // given: 테스트용 MimeMessage 생성
        val session = Session.getDefaultInstance(Properties())
        val mimeMessage =
            MimeMessage(session).apply {
                setFrom(InternetAddress("sender@example.com"))
                setRecipients(MimeMessage.RecipientType.TO, "recipient@example.com")
                subject = "Test Newsletter"
                setText("This is a test newsletter content")
                sentDate = Date()
            }
        val testMessageSource = TestMessageSource(GenericMessage(mimeMessage))

        val mailReader = MailReader(testMessageSource)

        // when
        val mail = mailReader.readMails()[0]

        // then
        with(mail) {
            assertEquals(listOf("sender@example.com"), from)
            assertEquals("Test Newsletter", subject)
            assertEquals("This is a test newsletter content", extractedContent)
            assertNotNull(sentDate)
        }
    }

    // 테스트용 MessageSource 구현체
    class TestMessageSource(
        private val message: Message<MimeMessage>?,
    ) : MessageSource<MimeMessage> {
        private var consumed = false

        override fun receive(): Message<MimeMessage>? =
            if (!consumed && message != null) {
                consumed = true
                message
            } else {
                null
            }
    }
}
