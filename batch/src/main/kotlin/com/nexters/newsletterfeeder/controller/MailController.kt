package com.nexters.newsletterfeeder.controller

import com.nexters.newsletterfeeder.scheduler.ScheduledMailReader
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailController(
    private val scheduledMailReader: ScheduledMailReader
) {
    @GetMapping("/read")
    fun readMails(): Map<String, String> {
        LOGGER.info("Manual email reading requested via API")
        return try {
            scheduledMailReader.triggerMorningSchedule()
            mapOf("status" to "success", "message" to "Email reading completed successfully")
        } catch (e: Exception) {
            LOGGER.error("Error reading emails via API", e)
            mapOf("status" to "error", "message" to "Failed to read emails: ${e.message}")
        }
    }

    @GetMapping("/status")
    fun getStatus(): Map<String, String> {
        return mapOf(
            "status" to "running",
            "service" to "Newsletter Feeder",
            "description" to "POP3 email reader using Spring Integration"
        )
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MailController::class.java)
    }
}
