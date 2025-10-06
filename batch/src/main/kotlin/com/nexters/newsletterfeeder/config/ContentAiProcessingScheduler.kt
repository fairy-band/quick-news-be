package com.nexters.newsletterfeeder.config

import com.nexters.newsletterfeeder.service.ContentAiProcessingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("prod", "dev")
class ContentAiProcessingScheduler(
    private val contentAiProcessingService: ContentAiProcessingService
) {
    private val logger = LoggerFactory.getLogger(ContentAiProcessingScheduler::class.java)

    @Scheduled(cron = "0 */10 * * * *") // 10분마다 실행
    fun processUnprocessedContents() {
        logger.info("Starting content AI processing scheduler")
        try {
            val result = contentAiProcessingService.processUnprocessedContents()
            logger.info(
                "Content AI processing completed: Processed ${result.processedCount} items, " +
                    "Errors: ${result.errorCount}, Remaining: ${result.remainingCount}"
            )
        } catch (e: Exception) {
            logger.error("Error during content AI processing", e)
        }
    }
}
