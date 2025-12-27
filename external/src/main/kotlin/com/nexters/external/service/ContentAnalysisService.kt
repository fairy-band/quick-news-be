package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.dto.ContentAnalysisResult
import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.exception.AiProcessingException
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentAnalysisService(
    private val rateLimiterService: GeminiRateLimiterService,
    private val summaryRepository: SummaryRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val candidateKeywordRepository: CandidateKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val gson: Gson = Gson(),
) {
    private val logger = LoggerFactory.getLogger(ContentAnalysisService::class.java)

    /**
     * 콘텐츠를 분석하여 요약, 헤드라인, 키워드를 한 번에 추출합니다.
     */
    fun analyzeContent(
        content: String,
        inputKeywords: List<String> = emptyList()
    ): ContentAnalysisResult {
        val models = GeminiModel.entries

        for (model in models) {
            try {
                logger.info("Trying to analyze content with model: ${model.modelName}")

                val response = rateLimiterService.executeWithRateLimit(inputKeywords, model, content)

                if (response != null) {
                    val responseText = response.text()
                    logger.info("Got response from ${model.modelName}: $responseText")

                    val analysisResult = parseJsonResponse(responseText, model)
                    if (analysisResult != null) {
                        logger.info("Successfully parsed response from ${model.modelName}")
                        return analysisResult
                    } else {
                        logger.warn("Failed to parse JSON response from ${model.modelName}")
                    }
                } else {
                    logger.warn("Got null response from ${model.modelName}")
                }
            } catch (_: RateLimitExceededException) {
                logger.warn("Rate limit exceeded for model ${model.modelName}, trying next model")
                // Rate limit 초과 시 다음 모델로 시도
                continue
            } catch (e: Exception) {
                logger.error("Error with model ${model.modelName}: ${e.message}", e)
            }
        }

        logger.error("All models failed to analyze content")
        throw AiProcessingException("Failed to analyze content: all models failed")
    }

    /**
     * 콘텐츠를 분석하고 결과를 저장합니다.
     */
    @Transactional
    fun analyzeAndSave(contentEntity: Content): ContentAnalysisResult {
        // 예약된 키워드 목록 가져오기
        val reservedKeywords =
            reservedKeywordRepository
                .findAll()
                .map { it.name }
                .toList()

        // 콘텐츠 분석 수행
        val result = analyzeContent(contentEntity.content, reservedKeywords)

        // 요약 저장
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

        // 매칭된 키워드 저장
        val matchedReservedKeywords = reservedKeywordRepository.findByNameIn(result.matchedKeywords)
        assignKeywordsToContent(contentEntity, matchedReservedKeywords)

        return result
    }

    /**
     * 예약된 키워드와 매칭되는 키워드를 찾습니다.
     */
    fun matchReservedKeywords(content: String): List<ReservedKeyword> {
        val reservedKeywords =
            reservedKeywordRepository
                .findAll()
                .map { it.name }
                .toList()

        val result = analyzeContent(content, reservedKeywords)
        return reservedKeywordRepository.findByNameIn(result.matchedKeywords)
    }

    /**
     * 콘텐츠에 키워드를 할당합니다.
     */
    @Transactional
    fun assignKeywordsToContent(
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

    /**
     * 후보 키워드를 예약 키워드로 승격합니다.
     */
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

    /**
     * 요약을 저장합니다.
     */
    fun saveSummary(summary: Summary): Summary = summaryRepository.save(summary)

    /**
     * 콘텐츠에 대한 요약을 조회합니다.
     */
    fun getPrioritizedSummaryByContent(content: Content): List<Summary> = summaryRepository.findByContent(content)

    private fun parseJsonResponse(
        responseText: String?,
        model: GeminiModel
    ): ContentAnalysisResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)

            val summary = jsonResponse["summary"] as? String ?: ""
            val provocativeHeadlines =
                (jsonResponse["provocativeHeadlines"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            val matchedKeywords =
                (jsonResponse["matchedKeywords"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            val suggestedKeywords =
                (jsonResponse["suggestedKeywords"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            val provocativeKeywords =
                (jsonResponse["provocativeKeywords"] as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()

            ContentAnalysisResult(
                summary = summary,
                provocativeHeadlines = provocativeHeadlines,
                matchedKeywords = matchedKeywords,
                suggestedKeywords = suggestedKeywords,
                provocativeKeywords = provocativeKeywords,
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
