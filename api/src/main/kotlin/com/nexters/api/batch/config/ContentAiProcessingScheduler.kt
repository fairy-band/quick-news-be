package com.nexters.api.batch.config

import com.nexters.api.batch.service.ContentAiProcessingService
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
class ContentAiProcessingScheduler(
    private val contentAiProcessingService: ContentAiProcessingService,
    private val geminiRateLimiterService: GeminiRateLimiterService,
) {
    private val logger = LoggerFactory.getLogger(ContentAiProcessingScheduler::class.java)

    @Scheduled(cron = "0 5,15,25,35,45,55 * * * *") // 매 10분 주기 중 5분 offset (05분, 15분, 25분...)
    fun processUnprocessedContents() {
        if (geminiRateLimiterService.areAllModelsBlocked()) {
            logger.info("Skipping content AI processing: every configured Gemini model is in backoff.")
            return
        }

        logger.info("Starting unified content AI processing pipeline (Summary + ExposureContent + Markdown)")
        try {
            val result = contentAiProcessingService.processUnprocessedContents()
            logger.info(
                "Unified content AI processing completed: Processed ${result.processedCount} items, " +
                    "Errors: ${result.errorCount}, Remaining: ${result.remainingCount}"
            )
        } catch (e: RateLimitExceededException) {
            logger.error(
                "Rate limit exceeded during content AI processing. " +
                    "Batch stopped. LimitType: ${e.limitType}, Model: ${e.modelName}",
                e
            )
            // RateLimitExceededException 발생 시 배치 중단 (예외를 다시 던지지 않음)
        } catch (e: Exception) {
            logger.error("Error during content AI processing", e)
        }
    }
}
