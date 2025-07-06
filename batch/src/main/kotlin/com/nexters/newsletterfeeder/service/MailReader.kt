package com.nexters.newsletterfeeder.service

import com.nexters.newsletterfeeder.dto.EmailMessage
import com.nexters.newsletterfeeder.scheduler.ScheduledMailReader
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.integration.core.MessageSource
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.GenericMessage
import org.springframework.stereotype.Service

@Service
class MailReader(
    val mailMessageSource: MessageSource<*>,
    val mailChannel: MessageChannel
) {
    fun read() {
        try {
            LOGGER.info("Checking for new emails...")

            // 메일 메시지 소스에서 메시지를 받아옴
            val message = mailMessageSource.receive()

            if (message != null) {
                LOGGER.info("Found new email message")
                // 메일 채널로 메시지 전송

                mailChannel.send(GenericMessage(EmailMessage.fromMimeMessage(message.payload as MimeMessage), message.headers))
            } else {
                LOGGER.info("No new emails found")
            }
        } catch (e: Exception) {
            LOGGER.error("Error reading emails", e)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ScheduledMailReader::class.java)
    }
}
