package com.nexters.external.dto

data class AutoContentEvaluationInput(
    val content: String,
    val generatedSummary: String,
    val generatedHeadlines: List<String>,
    val retryCount: Int,
)

data class BatchAutoContentEvaluationInput(
    val contentId: String,
    val content: String,
    val generatedSummary: String,
    val generatedHeadlines: List<String>,
    val retryCount: Int,
)
