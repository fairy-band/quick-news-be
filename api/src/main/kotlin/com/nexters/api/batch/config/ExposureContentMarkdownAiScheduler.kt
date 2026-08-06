package com.nexters.api.batch.config

import com.nexters.api.batch.service.ExposureContentMarkdownAiService
import com.nexters.external.exception.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ExposureContentMarkdownAiScheduler(
    private val markdownAiService: ExposureContentMarkdownAiService
) {
    private val logger = LoggerFactory.getLogger(ExposureContentMarkdownAiScheduler::class.java)

    @Scheduled(cron = "0 5,15,25,35,45,55 * * * *") // 매 10분 주기 중 5분 offset (05분, 15분, 25분...)
    fun processUnprocessedMarkdowns() {
        logger.info("Starting Exposure Content Markdown AI processing scheduler")
        try {
            markdownAiService.processUnprocessedMarkdowns()
            logger.info("Exposure Content Markdown AI processing completed successfully")
        } catch (e: RateLimitExceededException) {
            logger.error(
                "Rate limit exceeded during markdown AI processing. " +
                    "Batch stopped. LimitType: ${e.limitType}, Model: ${e.modelName}",
                e
            )
        } catch (e: Exception) {
            logger.error("Error during exposure content markdown AI processing", e)
        }
    }
}
