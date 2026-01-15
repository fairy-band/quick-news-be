package com.nexters.api.batch.service

import com.nexters.api.batch.dto.EmailMessage
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletter.service.NewsletterProcessingService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

data class MailProcessingResult(
    val success: Boolean,
    val emailMessage: EmailMessage,
    val newsletterId: String? = null,
    val errorMessage: String? = null,
    val processingTime: Long = System.currentTimeMillis(),
)

@Service
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MailProcessor(
    private val newsletterSourceService: NewsletterSourceService,
    private val newsletterProcessingService: NewsletterProcessingService,
) {
    fun saveNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val newsletterSource = convertToNewsletterSource(emailMessage)
        val savedNewsletter = newsletterSourceService.save(newsletterSource)
        logger.info("Newsletter saved successfully with ID: ${savedNewsletter.id}")
        return savedNewsletter
    }

    fun processNewsletterSource(newsletterSourceId: String) {
        newsletterProcessingService.processNewsletter(newsletterSourceId)
    }

    private fun convertToNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val senderInfo = parseSenderInfo(emailMessage.from.firstOrNull() ?: "Unknown")

        return NewsletterSource(
            subject = emailMessage.subject,
            sender = senderInfo.first,
            senderEmail = senderInfo.second,
            recipient = "newsletter.feeding@gmail.com", // 현재 설정된 수신 이메일
            recipientEmail = "newsletter.feeding@gmail.com",
            content = emailMessage.textContent ?: emailMessage.extractedContent, // 이메일은 기존처럼 extractedContent 사용
            contentType = emailMessage.contentType ?: "text/plain",
            htmlContent = emailMessage.htmlContent, // HTML 콘텐츠 할당
            receivedDate =
                emailMessage.sentDate?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
                    ?: LocalDateTime.now(),
            headers = emptyMap(), // EmailMessage에 headers 필드가 없음
        )
    }

    private fun parseSenderInfo(sender: String): Pair<String, String> {
        val emailMatch = emailRegex.find(sender)

        return if (emailMatch != null) {
            val email = emailMatch.groupValues[1]
            val name = sender.substringBefore("<").trim()
            name to email
        } else {
            sender to sender
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MailProcessor::class.java)
        private val emailRegex = "<(.+?)>".toRegex()
    }
}
