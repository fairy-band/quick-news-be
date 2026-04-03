package com.nexters.external.service

import com.nexters.external.dto.ContentAnalysisResult
import com.nexters.external.dto.LegacyKeywordAnalysisResult
import com.nexters.external.entity.Content
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.enums.ContentGenerationMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentAnalysisService(
    private val autoContentAnalysisPipelineService: AutoContentAnalysisPipelineService,
    private val legacyKeywordAnalysisService: LegacyKeywordAnalysisService,
    private val contentAnalysisPersistenceService: ContentAnalysisPersistenceService,
    private val contentKeywordService: ContentKeywordService,
) {
    private val logger = LoggerFactory.getLogger(ContentAnalysisService::class.java)

    fun analyzeContent(
        content: String,
        inputKeywords: List<String> = emptyList(),
    ): ContentAnalysisResult =
        autoContentAnalysisPipelineService
            .resolveSinglePipeline(content, inputKeywords)
            .last()
            .toFinalResult()

    fun analyzeLegacyKeywordDiscovery(
        content: String,
        inputKeywords: List<String> = emptyList(),
    ): LegacyKeywordAnalysisResult = legacyKeywordAnalysisService.analyze(content, inputKeywords)

    @Transactional
    fun analyzeAndSave(contentEntity: Content): ContentAnalysisResult {
        val reservedKeywords = contentKeywordService.getReservedKeywordNames()
        val attempts = autoContentAnalysisPipelineService.resolveSinglePipeline(contentEntity.content, reservedKeywords)
        val acceptedResult =
            contentAnalysisPersistenceService.persistAcceptedAnalysis(
                content = contentEntity,
                attempts = attempts,
                generationMode = ContentGenerationMode.SINGLE,
                skipWhenSummaryExists = false,
            )

        val matchedReservedKeywords = contentKeywordService.findReservedKeywordsByNames(acceptedResult.matchedKeywords)
        contentKeywordService.assignKeywordsToContent(contentEntity, matchedReservedKeywords)

        return acceptedResult
    }

    fun analyzeBatchAndSave(contentEntities: List<Content>): Map<String, ContentAnalysisResult> {
        if (contentEntities.isEmpty()) {
            logger.warn("No content entities provided for batch analysis and save")
            return emptyMap()
        }

        val reservedKeywords = contentKeywordService.getReservedKeywordNames()
        val histories = autoContentAnalysisPipelineService.resolveBatchPipeline(contentEntities, reservedKeywords)
        val contentMap = contentEntities.associateBy { it.id!!.toString() }
        val resultMap = mutableMapOf<String, ContentAnalysisResult>()

        histories.forEach { (contentId, attempts) ->
            val content = contentMap[contentId]
            if (content == null) {
                logger.warn("Content not found for contentId: $contentId")
                return@forEach
            }

            try {
                val acceptedResult =
                    contentAnalysisPersistenceService.persistAcceptedAnalysis(
                        content = content,
                        attempts = attempts,
                        generationMode = ContentGenerationMode.BATCH,
                        skipWhenSummaryExists = true,
                    )

                val matchedReservedKeywords = contentKeywordService.findReservedKeywordsByNames(acceptedResult.matchedKeywords)
                contentKeywordService.assignKeywordsToContent(content, matchedReservedKeywords)

                resultMap[contentId] = acceptedResult
                logger.info("Successfully saved analysis result for content ID: $contentId")
            } catch (e: Exception) {
                logger.error("Failed to save analysis result for content ID: $contentId", e)
            }
        }

        return resultMap
    }

    fun matchReservedKeywords(content: String): List<ReservedKeyword> {
        val reservedKeywords = contentKeywordService.getReservedKeywordNames()
        val result = analyzeLegacyKeywordDiscovery(content, reservedKeywords)
        return contentKeywordService.findReservedKeywordsByNames(result.matchedKeywords)
    }

    @Transactional
    fun assignKeywordsToContent(
        content: Content,
        matchedKeywords: List<ReservedKeyword>,
    ) = contentKeywordService.assignKeywordsToContent(content, matchedKeywords)

    @Transactional
    fun promoteCandidateKeyword(candidateKeywordId: Long): ReservedKeyword =
        contentKeywordService.promoteCandidateKeyword(candidateKeywordId)

    fun saveSummary(summary: Summary): Summary = contentAnalysisPersistenceService.saveSummary(summary)

    fun getPrioritizedSummaryByContent(content: Content): List<Summary> =
        contentAnalysisPersistenceService.getPrioritizedSummaryByContent(content)
}
