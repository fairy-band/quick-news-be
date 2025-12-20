package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.dto.SummaryResult
import com.nexters.external.entity.Content
import com.nexters.external.entity.Summary
import com.nexters.external.exception.AiProcessingException
import com.nexters.external.repository.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@Deprecated(
    message = "Use ContentAnalysisService instead. This service has been merged into ContentAnalysisService for unified content analysis.",
    replaceWith = ReplaceWith("ContentAnalysisService", "com.nexters.external.service.ContentAnalysisService")
)
class SummaryService(
    private val geminiClient: GeminiClient,
    private val summaryRepository: SummaryRepository,
    private val gson: Gson = Gson(),
) {
    private val logger = LoggerFactory.getLogger(SummaryService::class.java)

    fun summarize(content: String): SummaryResult {
        val models = GeminiModel.entries

        for (model in models) {
            try {
                logger.info("Trying to get summary with model: ${model.modelName}")

                val response = geminiClient.requestContentAnalysis(emptyList(), model, content)

                if (response != null) {
                    val responseText = response.text()
                    logger.info("Got response from ${model.modelName}: $responseText")

                    val summaryResponse = parseJsonResponse(responseText, model)
                    if (summaryResponse != null) {
                        logger.info("Successfully parsed response from ${model.modelName}")
                        return summaryResponse
                    } else {
                        logger.warn("Failed to parse JSON response from ${model.modelName}")
                    }
                } else {
                    logger.warn("Got null response from ${model.modelName}")
                }
            } catch (e: Exception) {
                logger.error("Error with model ${model.modelName}: ${e.message}", e)
            }
        }

        logger.error("All models failed to generate summary")
        throw AiProcessingException("Failed to generate summary: all models failed")
    }

    fun summarizeAndSave(contentEntity: Content): SummaryResult {
        val result = summarize(contentEntity.content)

        if (result.summary.isNotEmpty()) {
            val usedModel = result.usedModel?.modelName ?: "unknown"

            val summary =
                Summary(
                    content = contentEntity,
                    title = contentEntity.title,
                    summarizedContent = result.summary,
                    model = usedModel
                )

            summaryRepository.save(summary)
            logger.info("Saved summary for content ID: ${contentEntity.id}")
        } else {
            logger.warn("Not saving empty summary for content ID: ${contentEntity.id}")
        }

        return result
    }

    fun save(summary: Summary): Summary = summaryRepository.save(summary)

    fun getPrioritizedSummaryByContent(content: Content): List<Summary> = summaryRepository.findByContent(content)

    private fun parseJsonResponse(
        responseText: String?,
        model: GeminiModel
    ): SummaryResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            val summary = jsonResponse["summary"] as? String ?: ""
            val provocativeHeadlines =
                (jsonResponse["provocativeHeadlines"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

            SummaryResult(
                summary = summary,
                provocativeHeadlines = provocativeHeadlines,
                usedModel = model
            )
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse JSON response: $responseText", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error parsing response: $responseText", e)
            null
        }
}
