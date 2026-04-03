package com.nexters.external.service

import com.nexters.external.dto.GeminiModel
import com.nexters.external.exception.AiProcessingException
import com.nexters.external.exception.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GeminiModelFallbackExecutor {
    private val logger = LoggerFactory.getLogger(GeminiModelFallbackExecutor::class.java)

    fun <T> execute(
        actionName: String,
        block: (GeminiModel) -> T?,
    ): T {
        val models = GeminiModel.entries
        var allFailedDueToRateLimit = true
        var lastRateLimitException: RateLimitExceededException? = null

        for (model in models) {
            try {
                logger.info("Trying to $actionName with model: ${model.modelName}")
                val result = block(model)
                if (result != null) {
                    logger.info("Successfully completed $actionName with model: ${model.modelName}")
                    return result
                }
                allFailedDueToRateLimit = false
                logger.warn("$actionName returned null for model: ${model.modelName}")
            } catch (e: RateLimitExceededException) {
                logger.warn("Rate limit exceeded for model ${model.modelName} during $actionName, trying next model")
                lastRateLimitException = e
            } catch (e: Exception) {
                logger.error("Error while trying to $actionName with model ${model.modelName}: ${e.message}", e)
                allFailedDueToRateLimit = false
            }
        }

        if (allFailedDueToRateLimit && lastRateLimitException != null) {
            logger.error("All models failed due to rate limit while trying to $actionName")
            throw lastRateLimitException
        }

        throw AiProcessingException("Failed to $actionName: all models failed")
    }
}
