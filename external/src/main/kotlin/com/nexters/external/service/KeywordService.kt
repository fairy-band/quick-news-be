package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.dto.KeywordResult
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ReservedKeywordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class KeywordService(
    private val geminiClient: GeminiClient,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val candidateKeywordRepository: CandidateKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
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

    @Transactional
    fun assignKeywordsToContent(
        content: Content,
        matchedKeywords: List<ReservedKeyword>,
    ) {
        matchedKeywords.forEach { keyword ->
            // 이미 매핑되어 있는지 확인
            val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
            if (existingMapping == null) {
                // 새로운 매핑 생성
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
