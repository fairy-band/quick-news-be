package com.nexters.external.dto

data class ContentAnalysisResult(
    val summary: String,
    val provocativeHeadlines: List<String>,
    val matchedKeywords: List<String>,
    val suggestedKeywords: List<String>,
    val provocativeKeywords: List<String>,
    val usedModel: GeminiModel?,
)
