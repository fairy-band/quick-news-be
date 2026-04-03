package com.nexters.external.service

import com.google.gson.Gson
import com.nexters.external.dto.ContentAnalysisAttemptRecord
import com.nexters.external.dto.ContentAnalysisResult
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentGenerationAttempt
import com.nexters.external.entity.Summary
import com.nexters.external.enums.ContentGenerationMode
import com.nexters.external.repository.ContentGenerationAttemptRepository
import com.nexters.external.repository.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentAnalysisPersistenceService(
    private val summaryRepository: SummaryRepository,
    private val contentGenerationAttemptRepository: ContentGenerationAttemptRepository,
) {
    private val logger = LoggerFactory.getLogger(ContentAnalysisPersistenceService::class.java)
    private val gson = Gson()

    @Transactional
    fun persistAcceptedAnalysis(
        content: Content,
        attempts: List<ContentAnalysisAttemptRecord>,
        generationMode: ContentGenerationMode,
        skipWhenSummaryExists: Boolean,
    ): ContentAnalysisResult {
        val savedAttempts = persistAttempts(content, attempts, generationMode)
        val acceptedResult = attempts.last().toFinalResult()

        saveGeneratedSummary(
            content = content,
            result = acceptedResult,
            generationAttempt = savedAttempts.last(),
            skipWhenSummaryExists = skipWhenSummaryExists,
        )

        return acceptedResult
    }

    fun saveSummary(summary: Summary): Summary = summaryRepository.save(summary)

    fun getPrioritizedSummaryByContent(content: Content): List<Summary> = summaryRepository.findByContent(content)

    private fun persistAttempts(
        content: Content,
        attempts: List<ContentAnalysisAttemptRecord>,
        generationMode: ContentGenerationMode,
    ): List<ContentGenerationAttempt> =
        attempts.mapIndexed { index, attempt ->
            contentGenerationAttemptRepository.save(
                ContentGenerationAttempt(
                    content = content,
                    generationMode = generationMode,
                    attemptNumber = index + 1,
                    model = buildModelLabel(attempt),
                    promptVersion = attempt.generationPromptVersion,
                    generatedSummary = attempt.summary,
                    generatedHeadlines = gson.toJson(attempt.provocativeHeadlines),
                    matchedKeywords = gson.toJson(attempt.matchedKeywords),
                    qualityScore = attempt.evaluation.score,
                    qualityReason = attempt.evaluation.reason,
                    aiLikePatterns = gson.toJson(attempt.evaluation.aiLikePatterns),
                    recommendedFix = attempt.evaluation.recommendedFix,
                    passed = attempt.evaluation.passed,
                    accepted = index == attempts.lastIndex,
                    retryCount = attempt.evaluation.retryCount,
                ),
            )
        }

    private fun saveGeneratedSummary(
        content: Content,
        result: ContentAnalysisResult,
        generationAttempt: ContentGenerationAttempt,
        skipWhenSummaryExists: Boolean,
    ) {
        val existingSummaries = summaryRepository.findByContent(content)
        if (skipWhenSummaryExists && existingSummaries.isNotEmpty()) {
            logger.warn("Summary already exists for content ID: ${content.id}. Skipping save.")
            return
        }

        if (result.summary.isEmpty()) {
            logger.warn("Not saving empty summary for content ID: ${content.id}")
            return
        }

        val summary =
            Summary(
                content = content,
                title = result.provocativeHeadlines.firstOrNull() ?: content.title,
                summarizedContent = result.summary,
                generationAttempt = generationAttempt,
                qualityScore = result.qualityScore,
                qualityReason = result.qualityReason,
                retryCount = result.retryCount,
                model = result.usedModel?.modelName ?: generationAttempt.model,
            )

        summaryRepository.save(summary)
        logger.info("Saved summary for content ID: ${content.id}")
    }

    private fun buildModelLabel(attempt: ContentAnalysisAttemptRecord): String {
        val evaluationModel = attempt.evaluation.usedModel?.modelName
        val generationModel = attempt.generationModel?.modelName

        return when {
            generationModel != null && evaluationModel != null -> "$generationModel -> $evaluationModel"
            generationModel != null -> generationModel
            evaluationModel != null -> evaluationModel
            else -> "unknown"
        }
    }
}
