package com.nexters.external.dto

data class ContentAnalysisAttemptRecord(
    val summary: String,
    val provocativeHeadlines: List<String>,
    val matchedKeywords: List<String>,
    val evaluation: ContentEvaluationResult,
    val generationModel: GeminiModel?,
    val generationPromptVersion: String,
)
