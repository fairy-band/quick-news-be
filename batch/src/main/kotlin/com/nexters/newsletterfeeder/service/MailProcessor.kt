package com.nexters.newsletterfeeder.service

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class MailProcessor(
    private val newsletterSourceService: NewsletterSourceService,
) {
    fun processEmail(emailMessage: EmailMessage) {
        try {
            logger.info("Processing email from: ${emailMessage.from.joinToString(", ")}")
            logger.info("Subject: ${emailMessage.subject}")
            logger.info("Sent Date: ${emailMessage.sentDate}")

            val newsletterSource = convertToNewsletterSource(emailMessage)

            val savedNewsletter = newsletterSourceService.save(newsletterSource)
            logger.info("Newsletter saved successfully with ID: ${savedNewsletter.id}")
        } catch (e: IllegalArgumentException) {
            logger.warn("Newsletter already exists: ${e.message}")
        } catch (e: Exception) {
            logger.error("Error processing email", e)
        }
    }

    private fun convertToNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val senderInfo = parseSenderInfo(emailMessage.from.firstOrNull() ?: "Unknown")

        return NewsletterSource(
            subject = emailMessage.subject,
            sender = senderInfo.first,
            senderEmail = senderInfo.second,
            recipient = "newsletter.feeding@gmail.com", // 현재 설정된 수신 이메일
            recipientEmail = "newsletter.feeding@gmail.com",
            content = emailMessage.extractedContent,
            contentType = emailMessage.contentType ?: "text/plain",
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
