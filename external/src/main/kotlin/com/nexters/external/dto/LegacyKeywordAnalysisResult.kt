package com.nexters.external.dto

data class LegacyKeywordAnalysisResult(
    val matchedKeywords: List<String>,
    val suggestedKeywords: List<String>,
    val provocativeKeywords: List<String>,
    val usedModel: GeminiModel?,
)
