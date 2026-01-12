package com.nexters.api.batch.service

import com.nexters.api.batch.dto.EmailMessage
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.integration.core.MessageSource
import org.springframework.stereotype.Service

/**
 * 메일 수신 및 처리를 담당하는 서비스
 */
@Service
@Profile("prod")
class MailReader(
    private val mailMessageSource: MessageSource<*>
) {
    private val logger = LoggerFactory.getLogger(MailReader::class.java)

    fun readMails(): List<EmailMessage> {
        logger.info("메일 수신 시작")
        val messages = mutableListOf<EmailMessage>()

        repeat(MAX_FETCH_SIZE) { index ->
            val message = mailMessageSource.receive() ?: return messages

            try {
                val payload = message.payload
                when (payload) {
                    is MimeMessage -> {
                        logger.info("[${index + 1}] 메일 변환: ${payload.subject}")
                        val emailMessage = EmailMessage.fromMimeMessage(payload)
                        messages.add(emailMessage)
                    }
                    else -> {
                        logger.warn("[${index + 1}] 지원하지 않는 메시지 타입: ${payload.javaClass.name}")
                    }
                }
            } catch (e: Exception) {
                logger.error("[${index + 1}] 메일 변환 중 오류 발생: ${e.message}", e)
            }
        }

        logger.info("총 ${messages.size}개 메일 수신됨")
        return messages
    }

    companion object {
        const val MAX_FETCH_SIZE = 10
    }
}
