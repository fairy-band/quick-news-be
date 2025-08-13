package com.nexters.newsletterfeeder.service

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
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
class MailProcessor(
    private val newsletterSourceService: NewsletterSourceService,
) {
    fun processEmail(emailMessage: EmailMessage): MailProcessingResult {
        val startTime = System.currentTimeMillis()

        try {
            logger.info("Processing email from: ${emailMessage.from.joinToString(", ")}")
            logger.info("Subject: ${emailMessage.subject}")
            logger.info("Sent Date: ${emailMessage.sentDate}")

            return MailProcessingResult(
                success = true,
                emailMessage = emailMessage,
                processingTime = System.currentTimeMillis() - startTime,
            )
        } catch (e: IllegalArgumentException) {
            logger.warn("Newsletter already exists: ${e.message}")
            return MailProcessingResult(
                success = false,
                emailMessage = emailMessage,
                errorMessage = "Newsletter already exists: ${e.message}",
                processingTime = System.currentTimeMillis() - startTime,
            )
        } catch (e: Exception) {
            logger.error("Error processing email", e)
            return MailProcessingResult(
                success = false,
                emailMessage = emailMessage,
                errorMessage = "Error processing email: ${e.message}",
                processingTime = System.currentTimeMillis() - startTime,
            )
        }
    }

    fun saveNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val newsletterSource = convertToNewsletterSource(emailMessage)
        val savedNewsletter = newsletterSourceService.save(newsletterSource)
        logger.info("Newsletter saved successfully with ID: ${savedNewsletter.id}")
        return savedNewsletter
    }

    private fun convertToNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val senderInfo = parseSenderInfo(emailMessage.from.firstOrNull() ?: "Unknown")

        return NewsletterSource(
            subject = emailMessage.subject,
            sender = senderInfo.first,
            senderEmail = senderInfo.second,
            recipient = "newsletter.feeding@gmail.com", // 현재 설정된 수신 이메일
            recipientEmail = "newsletter.feeding@gmail.com",
            content = emailMessage.extractedContent, // 이메일은 기존처럼 extractedContent 사용
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
