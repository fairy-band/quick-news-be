package com.nexters.admin.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.metrics.MetricsEndpoint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/metrics")
class MetricsController(
    @Autowired private val metricsEndpoint: MetricsEndpoint
) {
    @GetMapping("/gemini")
    fun getGeminiMetrics(): ResponseEntity<GeminiMetricsData> =
        try {
            val metricsData =
                GeminiMetricsData(
                    requestDuration = getMetricValue("gemini.api.request.duration"),
                    totalRequests = getMetricValue("gemini.api.requests"),
                    successfulRequests = getMetricValue("gemini.api.requests", mapOf("success" to "true")),
                    failedRequests = getMetricValue("gemini.api.requests", mapOf("success" to "false")),
                    totalTokens = getMetricValue("gemini.api.tokens", mapOf("type" to "total")),
                    promptTokens = getMetricValue("gemini.api.tokens", mapOf("type" to "prompt")),
                    candidateTokens = getMetricValue("gemini.api.tokens", mapOf("type" to "candidates")),
                    totalCost = getMetricValue("gemini.api.cost", mapOf("type" to "total")),
                    inputCost = getMetricValue("gemini.api.cost", mapOf("type" to "input")),
                    outputCost = getMetricValue("gemini.api.cost", mapOf("type" to "output"))
                )
            ResponseEntity.ok(metricsData)
        } catch (e: Exception) {
            ResponseEntity.ok(GeminiMetricsData())
        }

    private fun getMetricValue(
        metricName: String,
        tags: Map<String, String> = emptyMap()
    ): Double =
        try {
            val metric = metricsEndpoint.metric(metricName, tags.map { "${it.key}:${it.value}" })
            metric?.measurements?.firstOrNull()?.value ?: 0.0
        } catch (e: Exception) {
            0.0
        }
}

data class GeminiMetricsData(
    val requestDuration: Double = 0.0,
    val totalRequests: Double = 0.0,
    val successfulRequests: Double = 0.0,
    val failedRequests: Double = 0.0,
    val totalTokens: Double = 0.0,
    val promptTokens: Double = 0.0,
    val candidateTokens: Double = 0.0,
    val totalCost: Double = 0.0,
    val inputCost: Double = 0.0,
    val outputCost: Double = 0.0
)
