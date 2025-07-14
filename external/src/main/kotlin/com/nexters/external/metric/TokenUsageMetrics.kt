package com.nexters.external.metric

import com.nexters.external.dto.GeminiModel

data class TokenUsageMetrics(
    val model: GeminiModel,
    val operation: String,
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int,
    val success: Boolean,
    val error: String? = null,
)
