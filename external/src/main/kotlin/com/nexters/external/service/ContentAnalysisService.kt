package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.dto.BatchContentAnalysisItem
import com.nexters.external.dto.BatchContentAnalysisResult
import com.nexters.external.dto.BatchContentItem
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
import kotlin.collections.emptyList

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
        var allFailedDueToRateLimit = true
        var lastRateLimitException: RateLimitExceededException? = null

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
                        allFailedDueToRateLimit = false
                    }
                } else {
                    logger.warn("Got null response from ${model.modelName}")
                    allFailedDueToRateLimit = false
                }
            } catch (e: RateLimitExceededException) {
                logger.warn("Rate limit exceeded for model ${model.modelName}, trying next model")
                lastRateLimitException = e
                // Rate limit 초과 시 다음 모델로 시도
                continue
            } catch (e: Exception) {
                logger.error("Error with model ${model.modelName}: ${e.message}", e)
                allFailedDueToRateLimit = false
            }
        }

        // 모든 모델이 Rate Limit으로 실패한 경우 RateLimitExceededException 던지기
        if (allFailedDueToRateLimit && lastRateLimitException != null) {
            logger.error("All models failed due to rate limit")
            throw lastRateLimitException
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
                    title = result.provocativeHeadlines.firstOrNull() ?: contentEntity.title,
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
     * 여러 콘텐츠를 한 번에 분석합니다. (배치 처리)
     * API 호출 횟수를 줄여 Rate Limit을 효율적으로 관리합니다.
     *
     * @param contentEntities 분석할 콘텐츠 엔티티 리스트
     * @param inputKeywords 매칭할 키워드 목록
     * @return contentId를 키로 하는 분석 결과 맵
     */
    fun analyzeBatchContent(
        contentEntities: List<Content>,
        inputKeywords: List<String> = emptyList()
    ): BatchContentAnalysisResult {
        if (contentEntities.isEmpty()) {
            logger.warn("No content entities provided for batch analysis")
            return BatchContentAnalysisResult(emptyMap(), null)
        }

        // Content 엔티티를 BatchContentItem으로 변환
        val contentItems =
            contentEntities.map { content ->
                BatchContentItem(
                    contentId = content.id!!.toString(),
                    content = content.content
                )
            }

        val models = GeminiModel.entries
        var allFailedDueToRateLimit = true
        var lastRateLimitException: RateLimitExceededException? = null

        for (model in models) {
            try {
                logger.info("Trying to analyze ${contentItems.size} contents with model: ${model.modelName}")

                val response = rateLimiterService.executeBatchWithRateLimit(inputKeywords, model, contentItems)

                if (response != null) {
                    val responseText = response.text()
                    logger.info("Got batch response from ${model.modelName}")

                    val analysisResult = parseBatchJsonResponse(responseText, model)
                    if (analysisResult != null) {
                        logger.info(
                            "Successfully parsed batch response from ${model.modelName} with ${analysisResult.results.size} results"
                        )
                        return analysisResult
                    } else {
                        logger.warn("Failed to parse batch JSON response from ${model.modelName}")
                        allFailedDueToRateLimit = false
                    }
                } else {
                    logger.warn("Got null response from ${model.modelName}")
                    allFailedDueToRateLimit = false
                }
            } catch (e: RateLimitExceededException) {
                logger.warn("Rate limit exceeded for model ${model.modelName}, trying next model")
                lastRateLimitException = e
                continue
            } catch (e: Exception) {
                logger.error("Error with model ${model.modelName}: ${e.message}", e)
                allFailedDueToRateLimit = false
            }
        }

        // 모든 모델이 Rate Limit으로 실패한 경우 RateLimitExceededException 던지기
        if (allFailedDueToRateLimit && lastRateLimitException != null) {
            logger.error("All models failed due to rate limit for batch processing")
            throw lastRateLimitException
        }

        logger.error("All models failed to analyze batch content")
        throw AiProcessingException("Failed to analyze batch content: all models failed")
    }

    /**
     * 여러 콘텐츠를 한 번에 분석하고 결과를 저장합니다. (배치 처리)
     * 트랜잭션을 분리하여 부분 실패 시에도 성공한 항목은 저장됩니다.
     *
     * @param contentEntities 분석할 콘텐츠 엔티티 리스트
     * @return contentId를 키로 하는 분석 결과 맵
     */
    fun analyzeBatchAndSave(contentEntities: List<Content>): Map<String, ContentAnalysisResult> {
        if (contentEntities.isEmpty()) {
            logger.warn("No content entities provided for batch analysis and save")
            return emptyMap()
        }

        // 예약된 키워드 목록 가져오기
        val reservedKeywords =
            reservedKeywordRepository
                .findAll()
                .map { it.name }
                .toList()

        // 배치 분석 수행 (트랜잭션 외부 - API 호출)
        val batchResult = analyzeBatchContent(contentEntities, reservedKeywords)

        // contentId를 키로 하는 Content 맵 생성 (빠른 조회를 위해)
        val contentMap = contentEntities.associateBy { it.id!!.toString() }

        // 결과를 저장하고 ContentAnalysisResult로 변환
        val resultMap = mutableMapOf<String, ContentAnalysisResult>()

        // 각 항목을 개별 트랜잭션으로 저장 (부분 실패 허용)
        batchResult.results.forEach { (contentId, item) ->
            val content = contentMap[contentId]
            if (content != null) {
                try {
                    // 개별 트랜잭션으로 저장
                    saveSingleAnalysisResult(content, item, batchResult.usedModel)

                    // ContentAnalysisResult로 변환하여 맵에 추가
                    resultMap[contentId] =
                        ContentAnalysisResult(
                            summary = item.summary,
                            provocativeHeadlines = item.provocativeHeadlines,
                            matchedKeywords = item.matchedKeywords,
                            suggestedKeywords = item.suggestedKeywords,
                            provocativeKeywords = item.provocativeKeywords,
                            usedModel = batchResult.usedModel
                        )

                    logger.info("Successfully saved analysis result for content ID: $contentId")
                } catch (e: Exception) {
                    logger.error("Failed to save analysis result for content ID: $contentId", e)
                    // 개별 실패는 무시하고 계속 진행
                }
            } else {
                logger.warn("Content not found for contentId: $contentId")
            }
        }

        return resultMap
    }

    /**
     * 단일 분석 결과를 개별 트랜잭션으로 저장합니다.
     * 부분 실패를 허용하여 배치 처리의 안정성을 높입니다.
     */
    @Transactional
    fun saveSingleAnalysisResult(
        content: Content,
        item: BatchContentAnalysisItem,
        usedModel: GeminiModel?
    ) {
        val contentId = content.id!!.toString()

        // 중복 저장 방지: 이미 Summary가 있는지 확인
        val existingSummaries = summaryRepository.findByContent(content)
        if (existingSummaries.isNotEmpty()) {
            logger.warn("Summary already exists for content ID: $contentId. Skipping save.")
            return
        }

        // 요약 저장
        if (item.summary.isNotEmpty()) {
            val modelName = usedModel?.modelName ?: "unknown"

            val summary =
                Summary(
                    content = content,
                    title = item.provocativeHeadlines.firstOrNull() ?: content.title,
                    summarizedContent = item.summary,
                    model = modelName
                )

            summaryRepository.save(summary)
            logger.debug("Saved summary for content ID: $contentId")
        } else {
            logger.warn("Not saving empty summary for content ID: $contentId")
        }

        // 매칭된 키워드 저장
        val matchedReservedKeywords = reservedKeywordRepository.findByNameIn(item.matchedKeywords)
        assignKeywordsToContent(content, matchedReservedKeywords)
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

    /**
     * 배치 분석 JSON 응답을 파싱합니다.
     */
    private fun parseBatchJsonResponse(
        responseText: String?,
        model: GeminiModel
    ): BatchContentAnalysisResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)

            val resultsArray = jsonResponse["results"] as? List<*> ?: emptyList<Any>()

            val resultsMap =
                resultsArray
                    .mapNotNull { obj ->
                        (obj as? Map<*, *>)?.let { map ->
                            (map["contentId"] as? String)?.let { id ->
                                id to
                                    BatchContentAnalysisItem(
                                        contentId = id,
                                        summary = map["summary"] as? String ?: "",
                                        provocativeHeadlines =
                                            (map["provocativeHeadlines"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                        matchedKeywords = (map["matchedKeywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                        suggestedKeywords =
                                            (map["suggestedKeywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                                        provocativeKeywords =
                                            (map["provocativeKeywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                                    )
                            }
                        }
                    }.toMap()

            BatchContentAnalysisResult(
                results = resultsMap,
                usedModel = model
            )
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse batch JSON response: $responseText", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error parsing batch response: $responseText", e)
            null
        }
}
