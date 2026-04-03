package com.nexters.external.dto

data class ContentEvaluationResult(
    val score: Int,
    val reason: String,
    val aiLikePatterns: List<String>,
    val recommendedFix: String,
    val passed: Boolean,
    val retryCount: Int,
    val usedModel: GeminiModel?,
)

data class BatchContentEvaluationItem(
    val contentId: String,
    val score: Int,
    val reason: String,
    val aiLikePatterns: List<String>,
    val recommendedFix: String,
    val passed: Boolean,
    val retryCount: Int,
)

data class BatchContentEvaluationResult(
    val results: Map<String, BatchContentEvaluationItem>,
    val usedModel: GeminiModel?,
)
