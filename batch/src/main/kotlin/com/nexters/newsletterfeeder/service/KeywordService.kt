package com.nexters.newsletterfeeder.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.newsletterfeeder.apiclient.GeminiClient
import com.nexters.newsletterfeeder.dto.GeminiModel
import com.nexters.newsletterfeeder.dto.KeywordResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class KeywordService(
    private val geminiClient: GeminiClient,
    private val gson: Gson = Gson()
) {
    private val logger = LoggerFactory.getLogger(KeywordService::class.java)

    fun extractKeywords(
        inputKeywords: List<String>,
        content: String
    ): KeywordResult {
        val models = GeminiModel.entries

        for (model in models) {
            try {
                logger.info("Trying to extract keywords with model: ${model.modelName}")

                val response = geminiClient.requestKeywords(inputKeywords, model, content)

                if (response != null) {
                    val responseText = response.text()
                    logger.info("Got response from ${model.modelName}: $responseText")

                    val keywordResponse = parseJsonResponse(responseText)
                    if (keywordResponse != null) {
                        logger.info("Successfully parsed response from ${model.modelName}")
                        return keywordResponse
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

        logger.error("All models failed to extract keywords")
        return KeywordResult(emptyList(), emptyList(), emptyList())
    }

    private fun parseJsonResponse(responseText: String?): KeywordResult? {
        return try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)

            val matchedKeywords =
                (jsonResponse["matchedKeywords"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            val suggestedKeywords =
                (jsonResponse["suggestedKeywords"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            val provocativeKeywords =
                (jsonResponse["provocativeKeywords"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

            KeywordResult(
                matchedKeywords = matchedKeywords,
                suggestedKeywords = suggestedKeywords,
                provocativeKeywords = provocativeKeywords,
            )
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse JSON response: $responseText", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error parsing response: $responseText", e)
            null
        }
    }
}
