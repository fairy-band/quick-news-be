package com.nexters.api.batch.service

import com.nexters.api.batch.dto.EmailMessage
import com.nexters.external.entity.EmailSource
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.repository.EmailSourceRepository
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletter.parser.MailParserFactory
import com.nexters.newsletter.service.NewsletterProcessingService
import jakarta.mail.internet.MimeUtility
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.LocalDateTime

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
    private val emailSourceRepository: EmailSourceRepository,
) {
    private val mailParserFactory = MailParserFactory()

    fun shouldProcess(emailMessage: EmailMessage): Boolean {
        val senderInfo = parseSenderInfo(emailMessage.from.firstOrNull() ?: "Unknown")
        val senderEmail = senderInfo.second

        // 1. Email Source Whitelist & Active Status Check
        val emailSource = emailSourceRepository.findBySenderEmail(senderEmail)
        if (emailSource != null && !emailSource.isActive) {
            logger.info(
                "Skipping deactivated email source. senderEmail={}, subject={}",
                senderEmail,
                emailMessage.subject,
            )
            return false
        }

        // 2. Mail Parser Support Check
        val parser = mailParserFactory.findParser(senderEmail, emailMessage.subject)
        if (parser == null) {
            logger.info(
                "Skipping unsupported newsletter mail. senderEmail={}, subject={}",
                senderEmail,
                emailMessage.subject,
            )
            return false
        }

        // 3. Duplicate Newsletter Source Check
        val newsletterSource = convertToNewsletterSource(emailMessage)
        val existingNewsletter =
            newsletterSourceService.findBySenderEmailAndSubjectAndReceivedDate(
                senderEmail = newsletterSource.senderEmail,
                subject = newsletterSource.subject,
                receivedDate = newsletterSource.receivedDate,
            )
        if (existingNewsletter != null) {
            logger.info(
                "Skipping duplicate newsletter source. id={}, senderEmail={}, subject={}, receivedDate={}",
                existingNewsletter.id,
                newsletterSource.senderEmail,
                newsletterSource.subject,
                newsletterSource.receivedDate,
            )
            return false
        }

        return true
    }

    fun saveNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val newsletterSource = convertToNewsletterSource(emailMessage)
        val existingNewsletter =
            newsletterSourceService.findBySenderEmailAndSubjectAndReceivedDate(
                senderEmail = newsletterSource.senderEmail,
                subject = newsletterSource.subject,
                receivedDate = newsletterSource.receivedDate,
            )
        if (existingNewsletter != null) {
            logger.info(
                "Skipping duplicate newsletter source. id={}, senderEmail={}, subject={}, receivedDate={}",
                existingNewsletter.id,
                newsletterSource.senderEmail,
                newsletterSource.subject,
                newsletterSource.receivedDate,
            )
            throw IllegalStateException("Duplicate newsletter source: ${existingNewsletter.id}")
        }

        val savedNewsletter = newsletterSourceService.save(newsletterSource)
        logger.info("Newsletter saved successfully with ID: ${savedNewsletter.id}")

        // Update lastReceivedAt or auto-register email source
        runCatching {
            val emailSource = emailSourceRepository.findBySenderEmail(newsletterSource.senderEmail)
            if (emailSource != null) {
                emailSource.lastReceivedAt = LocalDateTime.now()
                emailSourceRepository.save(emailSource)
            } else {
                val parser = mailParserFactory.findParser(newsletterSource.senderEmail, newsletterSource.subject)
                emailSourceRepository.save(
                    EmailSource(
                        name = newsletterSource.sender,
                        senderEmail = newsletterSource.senderEmail,
                        parserName = parser?.let { it::class.simpleName },
                        isActive = true,
                        lastReceivedAt = LocalDateTime.now(),
                    )
                )
            }
        }.onFailure { e ->
            logger.warn("Failed to update email source status for {}: {}", newsletterSource.senderEmail, e.message)
        }

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
                emailMessage.receivedDate
                    ?: emailMessage.sentDate
                    ?: LocalDateTime.now(),
            headers = emailMessage.headers,
        )
    }

    private fun parseSenderInfo(sender: String): Pair<String, String> {
        val decodedSender = runCatching { MimeUtility.decodeText(sender) }.getOrDefault(sender)
        val emailMatch = emailRegex.find(decodedSender) ?: emailRegex.find(sender)

        return if (emailMatch != null) {
            val email = emailMatch.groupValues[1]
            val name =
                decodedSender
                    .substringBefore("<")
                    .trim()
                    .trim('"')
                    .ifBlank { email }
            name to email
        } else {
            decodedSender to decodedSender
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MailProcessor::class.java)
        private val emailRegex = "<(.+?)>".toRegex()
    }
}
