package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.dto.KeywordResult
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ReservedKeywordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KeywordService(
    private val geminiClient: GeminiClient,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val candidateKeywordRepository: CandidateKeywordRepository,
    private val gson: Gson = Gson(),
) {
    private val logger = LoggerFactory.getLogger(KeywordService::class.java)

    fun matchReservedKeywords(content: String): List<ReservedKeyword> {
        val reservedKeywords =
            reservedKeywordRepository
                .findAll()
                .map { it.name }
                .toList()

        val keywordResult = extractKeywords(reservedKeywords, content)
        return reservedKeywordRepository.findByNameIn(keywordResult.matchedKeywords)
    }

    fun extractKeywords(
        inputKeywords: List<String>,
        content: String,
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

    @Transactional
    fun promoteCandidateKeyword(candidateKeywordId: Long): ReservedKeyword {
        val candidateKeyword =
            candidateKeywordRepository
                .findById(candidateKeywordId)
                .orElseThrow { NoSuchElementException("CandidateKeyword not found with id: $candidateKeywordId") }

        val reservedKeyword =
            reservedKeywordRepository.findByName(candidateKeyword.name)
                ?: ReservedKeyword(name = candidateKeyword.name).also {
                    reservedKeywordRepository.save(it)
                }

        candidateKeywordRepository.delete(candidateKeyword)

        return reservedKeyword
    }

    private fun parseJsonResponse(responseText: String?): KeywordResult? =
        try {
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
