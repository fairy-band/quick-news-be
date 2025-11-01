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

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정에 실행
    fun processRssWithAi() {
        logger.info("Starting RSS AI processing scheduler")
        try {
            val result = contentAiProcessingService.processRssWithAi()
            logger.info("RSS AI processing completed: $result")
        } catch (e: Exception) {
            logger.error("Error during RSS AI processing", e)
        }
    }

    @Scheduled(cron = "0 0 12 * * *") // 매일 정오에 실행
    fun logProcessingStats() {
        try {
            val stats = contentAiProcessingService.getRssProcessingStats()
            logger.info("RSS AI Processing Stats - Processed today: ${stats.processedToday}/${stats.dailyLimit}, Pending: ${stats.pending}")
        } catch (e: Exception) {
            logger.error("Error getting RSS processing stats", e)
        }
    }
}
