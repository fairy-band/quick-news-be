package com.nexters.external.dto

data class ContentAnalysisResult(
    val summary: String,
    val provocativeHeadlines: List<String>,
    val matchedKeywords: List<String>,
    val qualityScore: Int,
    val qualityReason: String,
    val aiLikePatterns: List<String>,
    val recommendedFix: String,
    val passed: Boolean,
    val retryCount: Int,
    val usedModel: GeminiModel?,
)
