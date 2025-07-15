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
    val mailChannel: MessageChannel,
) {
    fun read() {
        try {
            LOGGER.info("=== 메일 읽기 시작 (최대 10개 ) ===")
            var count = 0
            val maxFetchSize = 10 // MailChannelConfig의 DEFAULT_FETCH_MAIL_SIZE와 동일

            repeat(maxFetchSize) { index ->
                val message = mailMessageSource.receive()

                if (message != null) {
                    count++
                    LOGGER.info("[$count] 메일 처리 중...")

                    val emailMessage = EmailMessage.fromMimeMessage(message.payload as MimeMessage)
                    LOGGER.info("[$count] 제목: ${emailMessage.subject}")

                    mailChannel.send(GenericMessage(emailMessage, message.headers))
                } else {
                    LOGGER.info("${index + 1}번째 시도에서 메일 없음 - 중단")
                    return@repeat
                }
            }

            LOGGER.info("=== 총 ${count}개 메일 처리 완료 ===")

        } catch (e: Exception) {
            LOGGER.error("메일 읽기 중 오류 발생", e)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ScheduledMailReader::class.java)
    }
}
