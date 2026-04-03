package com.nexters.external.service

import com.nexters.external.dto.LegacyKeywordAnalysisResult
import org.springframework.stereotype.Service

@Service
class LegacyKeywordAnalysisService(
    private val modelFallbackExecutor: GeminiModelFallbackExecutor,
    private val rateLimiterService: GeminiRateLimiterService,
    private val responseParser: ContentAnalysisResponseParser,
) {
    fun analyze(
        content: String,
        inputKeywords: List<String> = emptyList(),
    ): LegacyKeywordAnalysisResult =
        modelFallbackExecutor.execute("analyze legacy keywords") { model ->
            val response = rateLimiterService.executeLegacyKeywordDiscoveryWithRateLimit(inputKeywords, model, content)
            val responseText = response?.text()
            responseParser.parseLegacyKeywordDiscoveryResponse(responseText, model)
        }
}
