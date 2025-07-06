package com.nexters.newsletterfeeder.service

import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MailProcessor {
    fun processEmail(emailMessage: EmailMessage) {
        try {
            LOGGER.info("Processing email from: ${emailMessage.from.joinToString(", ")}")
            LOGGER.info("Subject: ${emailMessage.subject}")
            LOGGER.info("Sent Date: ${emailMessage.sentDate}")
            val content = emailMessage.extractedContent
        } catch (e: Exception) {
            LOGGER.error("Error processing email", e)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MailProcessor::class.java)
    }
}
