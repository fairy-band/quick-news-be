package com.nexters.external.service

import com.google.gson.Gson
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.ContentAnalysisResult
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentGenerationAttempt
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.enums.ContentGenerationMode
import com.nexters.external.repository.ContentGenerationAttemptRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentAnalysisPersister(
    private val summaryRepository: SummaryRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val contentGenerationAttemptRepository: ContentGenerationAttemptRepository,
    private val gson: Gson = Gson(),
) {
    private val logger = LoggerFactory.getLogger(ContentAnalysisPersister::class.java)

    @Transactional
    fun persist(
        content: Content,
        attempts: List<ContentAnalysisAttempt>,
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

        val matchedReservedKeywords = reservedKeywordRepository.findByNameIn(acceptedResult.matchedKeywords)
        if (acceptedResult.matchedKeywords.isNotEmpty() && matchedReservedKeywords.isEmpty()) {
            logger.warn(
                "All matched keywords missing from reserved vocab. content={}, matched={}",
                content.id,
                acceptedResult.matchedKeywords,
            )
        }
        assignKeywordsToContent(content, matchedReservedKeywords)

        return acceptedResult
    }

    private fun persistAttempts(
        content: Content,
        attempts: List<ContentAnalysisAttempt>,
        generationMode: ContentGenerationMode,
    ): List<ContentGenerationAttempt> =
        attempts.mapIndexed { index, attempt ->
            val evaluationModel = attempt.evaluation.usedModel?.modelName
            val generationModel = attempt.generationModel?.modelName
            contentGenerationAttemptRepository.save(
                ContentGenerationAttempt(
                    content = content,
                    generationMode = generationMode,
                    attemptNumber = index + 1,
                    model =
                        when {
                            generationModel != null && evaluationModel != null -> "$generationModel -> $evaluationModel"
                            generationModel != null -> generationModel
                            evaluationModel != null -> evaluationModel
                            else -> "unknown"
                        },
                    promptVersion = GeminiClient.AUTO_GENERATION_PROMPT_VERSION,
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

        summaryRepository.save(
            Summary(
                content = content,
                title = result.provocativeHeadlines.firstOrNull() ?: content.title,
                summarizedContent = result.summary,
                generationAttempt = generationAttempt,
                qualityScore = result.qualityScore,
                qualityReason = result.qualityReason,
                retryCount = result.retryCount,
                model = result.usedModel?.modelName ?: generationAttempt.model,
            ),
        )
    }

    private fun assignKeywordsToContent(
        content: Content,
        matchedKeywords: List<ReservedKeyword>,
    ) {
        matchedKeywords.forEach { keyword ->
            val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
            if (existingMapping == null) {
                val mapping =
                    ContentKeywordMapping(
                        content = content,
                        keyword = keyword,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                    )
                contentKeywordMappingRepository.save(mapping)
                logger.debug("Assigned keyword '${keyword.name}' to content ID: ${content.id}")
            } else {
                logger.debug("Keyword '${keyword.name}' already assigned to content ID: ${content.id}")
            }
        }
    }
}
