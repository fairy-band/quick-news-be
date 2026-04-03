package com.nexters.external.service

import com.nexters.external.dto.ContentAnalysisAttemptRecord
import com.nexters.external.dto.ContentAnalysisResult

internal fun ContentAnalysisAttemptRecord.toFinalResult(): ContentAnalysisResult =
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
