package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.dto.SummaryResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SummaryService(
    private val geminiClient: GeminiClient,
    private val gson: Gson = Gson(),
) {
    private val logger = LoggerFactory.getLogger(SummaryService::class.java)

    fun getSummary(content: String): SummaryResult {
        val models = GeminiModel.entries

        for (model in models) {
            try {
                logger.info("Trying to get summary with model: ${model.modelName}")

                val response = geminiClient.requestSummary(model, content)

                if (response != null) {
                    val responseText = response.text()
                    logger.info("Got response from ${model.modelName}: $responseText")

                    val summaryResponse = parseJsonResponse(responseText)
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
        return SummaryResult("", listOf())
    }

    private fun parseJsonResponse(responseText: String?): SummaryResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            val summary = jsonResponse["summary"] as? String ?: ""
            val provocativeHeadlines =
                (jsonResponse["provocativeHeadlines"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

            SummaryResult(
                summary = summary,
                provocativeHeadlines = provocativeHeadlines,
            )
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse JSON response: $responseText", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error parsing response: $responseText", e)
            null
        }
}
