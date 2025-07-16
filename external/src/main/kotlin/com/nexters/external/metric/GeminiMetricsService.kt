package com.nexters.external.metric

import com.google.genai.types.GenerateContentResponse
import com.nexters.external.dto.GeminiModel
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GeminiMetricsService(
    private val meterRegistry: MeterRegistry
) {
    fun <T> measureApiCall(
        model: GeminiModel,
        operation: String,
        execution: () -> T
    ): T {
        var timerSample: Timer.Sample? = null

        try {
            timerSample = Timer.start(meterRegistry)
        } catch (e: Exception) {
            logger.warn("Failed to start timer for ${model.modelName}/$operation", e)
        }

        return try {
            val result = execution()
            recordSuccessMetrics(result, model, operation, timerSample)
            result
        } catch (e: Exception) {
            recordFailureMetrics(model, operation, timerSample, e)
            throw e
        }
    }

    private fun recordTokenUsage(metrics: TokenUsageMetrics) {
        val tags =
            arrayOf(
                "model",
                metrics.model.modelName,
                "operation",
                metrics.operation,
                "success",
                metrics.success.toString()
            )

        meterRegistry
            .counter("gemini.api.tokens", "type", "prompt", *tags)
            .increment(metrics.promptTokenCount.toDouble())

        meterRegistry
            .counter("gemini.api.tokens", "type", "candidates", *tags)
            .increment(metrics.candidatesTokenCount.toDouble())

        meterRegistry
            .counter("gemini.api.tokens", "type", "total", *tags)
            .increment(metrics.totalTokenCount.toDouble())

        val requestTags =
            if (metrics.success) {
                arrayOf(
                    "model",
                    metrics.model.modelName,
                    "operation",
                    metrics.operation,
                    "success",
                    "true"
                )
            } else {
                arrayOf(
                    "model",
                    metrics.model.modelName,
                    "operation",
                    metrics.operation,
                    "success",
                    "false",
                    "error",
                    metrics.error ?: "unknown"
                )
            }

        meterRegistry.counter("gemini.api.requests", *requestTags).increment()
    }

    private fun recordTokenCost(metrics: TokenUsageMetrics) {
        val pricing = MODEL_PRICING[metrics.model] ?: return

        val inputCost = (metrics.promptTokenCount / 1_000_000.0) * pricing.inputCostPer1M
        val outputCost = (metrics.candidatesTokenCount / 1_000_000.0) * pricing.outputCostPer1M
        val totalCost = inputCost + outputCost

        val tags =
            arrayOf(
                "model",
                metrics.model.modelName,
                "operation",
                metrics.operation
            )

        meterRegistry
            .counter("gemini.api.cost", "type", "input", *tags)
            .increment(inputCost)

        meterRegistry
            .counter("gemini.api.cost", "type", "output", *tags)
            .increment(outputCost)

        meterRegistry
            .counter("gemini.api.cost", "type", "total", *tags)
            .increment(totalCost)
    }

    private fun recordSuccessMetrics(
        result: Any?,
        model: GeminiModel,
        operation: String,
        timerSample: Timer.Sample?
    ) {
        try {
            if (result is GenerateContentResponse?) {
                if (result != null) {
                    recordTokenUsageFromResponse(result, model, operation, true)
                } else {
                    recordTokenUsage(
                        TokenUsageMetrics(
                            model = model,
                            operation = operation,
                            promptTokenCount = 0,
                            candidatesTokenCount = 0,
                            totalTokenCount = 0,
                            success = false,
                            error = "No response"
                        )
                    )
                }
            }

            completeTimer(timerSample, model, operation, true)
        } catch (e: Exception) {
            logger.warn("Failed to record success metrics for ${model.modelName}/$operation", e)
        }
    }

    private fun recordFailureMetrics(
        model: GeminiModel,
        operation: String,
        timerSample: Timer.Sample?,
        exception: Exception
    ) {
        try {
            recordTokenUsage(
                TokenUsageMetrics(
                    model = model,
                    operation = operation,
                    promptTokenCount = 0,
                    candidatesTokenCount = 0,
                    totalTokenCount = 0,
                    success = false,
                    error = exception.message
                )
            )
            completeTimer(timerSample, model, operation, false, exception.javaClass.simpleName)
        } catch (e: Exception) {
            logger.warn("Failed to record failure metrics for ${model.modelName}/$operation", e)
        }
    }

    private fun recordTokenUsageFromResponse(
        response: GenerateContentResponse,
        model: GeminiModel,
        operation: String,
        success: Boolean
    ) {
        try {
            val usageMetadata = response.usageMetadata()

            val tokenMetrics =
                TokenUsageMetrics(
                    model = model,
                    operation = operation,
                    promptTokenCount = usageMetadata?.flatMap { it.promptTokenCount() }?.orElse(null) ?: 0,
                    candidatesTokenCount = usageMetadata?.flatMap { it.candidatesTokenCount() }?.orElse(null) ?: 0,
                    totalTokenCount = usageMetadata?.flatMap { it.totalTokenCount() }?.orElse(null) ?: 0,
                    success = success,
                    error = null,
                )

            recordTokenUsage(tokenMetrics)

            if (success) {
                recordTokenCost(tokenMetrics)
            }
        } catch (e: Exception) {
            logger.error("Failed to record token usage from response for ${model.modelName}/$operation", e)
        }
    }

    private fun completeTimer(
        timerSample: Timer.Sample?,
        model: GeminiModel,
        operation: String,
        success: Boolean,
        errorType: String? = null
    ) {
        try {
            timerSample?.let {
                val tags =
                    if (success) {
                        arrayOf(
                            "model",
                            model.modelName,
                            "operation",
                            operation,
                            "success",
                            "true"
                        )
                    } else {
                        arrayOf(
                            "model",
                            model.modelName,
                            "operation",
                            operation,
                            "success",
                            "false",
                            "error_type",
                            errorType ?: "unknown"
                        )
                    }

                it.stop(
                    Timer
                        .builder("gemini.api.request.duration")
                        .tags(*tags)
                        .register(meterRegistry)
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to complete timer for ${model.modelName}/$operation", e)
        }
    }

    private data class ModelPricing(
        val inputCostPer1M: Double,
        val outputCostPer1M: Double
    )

    companion object {
        private val MODEL_PRICING =
            mapOf(
                GeminiModel.TWO_ZERO_FLASH_LITE to ModelPricing(0.075, 0.30),
                GeminiModel.TWO_ZERO_FLASH to ModelPricing(1.25, 5.00),
                GeminiModel.TWO_FIVE_FLASH to ModelPricing(0.075, 0.30)
            )
        private val logger = LoggerFactory.getLogger(GeminiMetricsService::class.java)
    }
}
