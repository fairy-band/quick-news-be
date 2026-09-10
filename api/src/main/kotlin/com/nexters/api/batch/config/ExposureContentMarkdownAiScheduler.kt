package com.nexters.api.batch.config

import com.nexters.api.batch.service.ExposureContentMarkdownAiService
import com.nexters.external.dto.GeminiModel
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.service.GeminiRateLimiterService
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
    private val markdownAiService: ExposureContentMarkdownAiService,
    private val geminiRateLimiterService: GeminiRateLimiterService,
) {
    private val logger = LoggerFactory.getLogger(ExposureContentMarkdownAiScheduler::class.java)

    @Scheduled(cron = "0 */10 * * * *") // 10분마다 실행
    fun processUnprocessedMarkdowns() {
        if (geminiRateLimiterService.isBlocked(GeminiModel.TWO_FIVE_FLASH)) {
            logger.info("Skipping markdown AI processing: ${GeminiModel.TWO_FIVE_FLASH.modelName} is in backoff.")
            return
        }

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
