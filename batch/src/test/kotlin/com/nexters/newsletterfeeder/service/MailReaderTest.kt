package com.nexters.newsletterfeeder.service

import com.nexters.newsletterfeeder.dto.EmailMessage
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.springframework.integration.core.MessageSource
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
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

        // 테스트용 MessageSource와 MessageChannel 생성
        val testMessageCaptor = TestMessageCaptor()
        val testMessageSource = TestMessageSource(GenericMessage(mimeMessage))
        val testMessageChannel = TestMessageChannel(testMessageCaptor)

        // MailReader 인스턴스 생성 (생성자 주입)
        val mailReader = MailReader(testMessageSource, testMessageChannel)

        // when
        mailReader.read()

        // then: 가독성 좋은 Kotlin스러운 검증
        val capturedMessage =
            requireNotNull(testMessageCaptor.getCapturedMessage()) {
                "No message was captured"
            }

        val emailMessage =
            capturedMessage.payload as? EmailMessage
                ?: error("Expected EmailMessage but got ${capturedMessage.payload?.javaClass?.simpleName}")

        with(emailMessage) {
            assertEquals(listOf("sender@example.com"), from)
            assertEquals("Test Newsletter", subject)
            assertEquals("This is a test newsletter content", extractedContent)
            assertNotNull(sentDate)
        }
    }

    // 테스트용 MessageSource 구현체
    class TestMessageSource(private val message: Message<MimeMessage>?) : MessageSource<MimeMessage> {
        private var consumed = false

        override fun receive(): Message<MimeMessage>? {
            return if (!consumed && message != null) {
                consumed = true
                message
            } else {
                null
            }
        }
    }

    // 테스트용 MessageChannel 구현체
    class TestMessageChannel(private val messageCaptor: TestMessageCaptor) : MessageChannel {
        override fun send(message: Message<*>): Boolean {
            messageCaptor.captureMessage(message)
            return true
        }

        override fun send(
            message: Message<*>,
            timeout: Long
        ): Boolean {
            return send(message)
        }
    }

    // 간단한 메시지 캡처 클래스
    class TestMessageCaptor {
        private var capturedMessage: Message<*>? = null

        fun captureMessage(message: Message<*>) {
            capturedMessage = message
        }

        fun getCapturedMessage(): Message<*>? {
            return capturedMessage
        }
    }
}
