package com.nexters.external.service

import com.nexters.external.dto.ContentAnalysisResult
import com.nexters.external.dto.ContentEvaluationResult
import com.nexters.external.dto.GeminiModel

data class ContentAnalysisAttempt(
    val summary: String,
    val provocativeHeadlines: List<String>,
    val matchedKeywords: List<String>,
    val evaluation: ContentEvaluationResult,
    val generationModel: GeminiModel?,
) {
    fun toFinalResult(): ContentAnalysisResult =
        ContentAnalysisResult(
            summary = summary,
            provocativeHeadlines = provocativeHeadlines,
            matchedKeywords = matchedKeywords,
            qualityScore = evaluation.score,
            qualityReason = evaluation.reason,
            aiLikePatterns = evaluation.aiLikePatterns,
            recommendedFix = evaluation.recommendedFix,
            passed = evaluation.passed,
            retryCount = evaluation.retryCount,
            usedModel = generationModel ?: evaluation.usedModel,
        )
}
