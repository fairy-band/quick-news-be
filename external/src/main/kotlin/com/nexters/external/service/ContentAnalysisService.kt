package com.nexters.external.service

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.config.AiGeminiContentAnalysisProperties
import com.nexters.external.dto.AutoContentEvaluationInput
import com.nexters.external.dto.BatchAutoContentEvaluationInput
import com.nexters.external.dto.BatchContentAnalysisItem
import com.nexters.external.dto.BatchContentAnalysisResult
import com.nexters.external.dto.BatchContentEvaluationItem
import com.nexters.external.dto.BatchContentEvaluationResult
import com.nexters.external.dto.BatchContentItem
import com.nexters.external.dto.ContentAnalysisResult
import com.nexters.external.dto.ContentEvaluationResult
import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentGenerationAttempt
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.enums.ContentGenerationMode
import com.nexters.external.exception.AiProcessingException
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentGenerationAttemptRepository
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
    private val contentGenerationAttemptRepository: ContentGenerationAttemptRepository,
    private val contentAnalysisProperties: AiGeminiContentAnalysisProperties,
    private val gson: Gson = Gson(),
) {
    private val logger = LoggerFactory.getLogger(ContentAnalysisService::class.java)

    fun analyzeContent(
        content: String,
        inputKeywords: List<String> = emptyList(),
    ): ContentAnalysisResult = resolveSinglePipeline(content, inputKeywords).last().toFinalResult()

    @Transactional
    fun analyzeAndSave(contentEntity: Content): ContentAnalysisResult {
        val attempts = resolveSinglePipeline(contentEntity.content, getReservedKeywordNames())
        val savedAttempts = persistAttempts(contentEntity, attempts, ContentGenerationMode.SINGLE)
        val acceptedResult = attempts.last().toFinalResult()

        saveGeneratedSummary(
            content = contentEntity,
            result = acceptedResult,
            generationAttempt = savedAttempts.last(),
            skipWhenSummaryExists = false,
        )

        assignKeywordsToContent(
            contentEntity,
            reservedKeywordRepository.findByNameIn(acceptedResult.matchedKeywords),
        )

        return acceptedResult
    }

    fun analyzeBatchAndSave(contentEntities: List<Content>): Map<String, ContentAnalysisResult> {
        if (contentEntities.isEmpty()) {
            logger.warn("No content entities provided for batch analysis and save")
            return emptyMap()
        }

        val histories = resolveBatchPipeline(contentEntities, getReservedKeywordNames())
        val contentMap = contentEntities.associateBy { it.id!!.toString() }
        val resultMap = mutableMapOf<String, ContentAnalysisResult>()

        histories.forEach { (contentId, attempts) ->
            val content = contentMap[contentId]
            if (content == null) {
                logger.warn("Content not found for contentId: $contentId")
                return@forEach
            }

            try {
                val savedAttempts = persistAttempts(content, attempts, ContentGenerationMode.BATCH)
                val acceptedResult = attempts.last().toFinalResult()

                saveGeneratedSummary(
                    content = content,
                    result = acceptedResult,
                    generationAttempt = savedAttempts.last(),
                    skipWhenSummaryExists = true,
                )

                assignKeywordsToContent(
                    content,
                    reservedKeywordRepository.findByNameIn(acceptedResult.matchedKeywords),
                )

                resultMap[contentId] = acceptedResult
                logger.info("Successfully saved analysis result for content ID: $contentId")
            } catch (e: Exception) {
                logger.error("Failed to save analysis result for content ID: $contentId", e)
            }
        }

        return resultMap
    }

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

    fun saveSummary(summary: Summary): Summary = summaryRepository.save(summary)

    fun getPrioritizedSummaryByContent(content: Content): List<Summary> = summaryRepository.findByContent(content)

    private fun resolveSinglePipeline(
        content: String,
        inputKeywords: List<String>,
    ): List<ContentAnalysisAttempt> {
        val attempts = mutableListOf<ContentAnalysisAttempt>()
        val totalAttempts = contentAnalysisProperties.maxRegenerationAttempts.coerceAtLeast(0) + 1
        var additionalAvoidPatterns = emptyList<String>()

        repeat(totalAttempts) { retryCount ->
            val attempt = resolveSingleAttempt(content, inputKeywords, retryCount, additionalAvoidPatterns)
            attempts.add(attempt)

            if (attempt.evaluation.passed) {
                return attempts
            }

            additionalAvoidPatterns = (additionalAvoidPatterns + attempt.evaluation.aiLikePatterns).normalizedAvoidPatterns()
        }

        return attempts
    }

    private fun resolveSingleAttempt(
        content: String,
        inputKeywords: List<String>,
        retryCount: Int,
        additionalAvoidPatterns: List<String> = emptyList(),
    ): ContentAnalysisAttempt {
        val generated =
            withModelFallback("generate auto content") { model ->
                val response = rateLimiterService.executeAutoGeneration(inputKeywords, model, content, additionalAvoidPatterns)
                parseAutoGenerationResponse(response?.text())?.let { draft ->
                    GeneratedDraftWithModel(draft, model)
                }
            }

        val evaluation =
            withModelFallback("evaluate auto content") { model ->
                val response =
                    rateLimiterService.executeAutoEvaluation(
                        model = model,
                        input =
                            AutoContentEvaluationInput(
                                content = content,
                                generatedSummary = generated.draft.summary,
                                generatedHeadlines = generated.draft.provocativeHeadlines,
                                retryCount = retryCount,
                            ),
                    )

                parseEvaluationResponse(response?.text(), model)?.normalized(retryCount)
            }

        return ContentAnalysisAttempt(
            summary = generated.draft.summary,
            provocativeHeadlines = generated.draft.provocativeHeadlines,
            matchedKeywords = generated.draft.matchedKeywords,
            evaluation = evaluation,
            generationModel = generated.model,
        )
    }

    private fun resolveBatchPipeline(
        contentEntities: List<Content>,
        inputKeywords: List<String>,
    ): Map<String, List<ContentAnalysisAttempt>> {
        val contentItems =
            contentEntities.map { content ->
                BatchContentItem(
                    contentId = content.id!!.toString(),
                    content = content.content,
                )
            }

        val batchGeneration = resolveBatchGeneration(contentItems, inputKeywords)
        val batchEvaluation = resolveBatchEvaluation(contentItems, batchGeneration)

        return contentItems.associate { item ->
            val initialAttempt =
                if (batchGeneration.results.containsKey(item.contentId) && batchEvaluation.containsKey(item.contentId)) {
                    buildBatchAttempt(item, batchGeneration, batchEvaluation)
                } else {
                    logger.warn("Batch result missing for contentId=${item.contentId}. Falling back to single generation path.")
                    resolveSingleAttempt(
                        content = item.content,
                        inputKeywords = inputKeywords,
                        retryCount = 0,
                    )
                }
            val attempts = mutableListOf(initialAttempt)

            for (retryCount in 1..contentAnalysisProperties.maxRegenerationAttempts.coerceAtLeast(0)) {
                if (attempts.last().evaluation.passed) {
                    break
                }

                attempts.add(
                    resolveSingleAttempt(
                        content = item.content,
                        inputKeywords = inputKeywords,
                        retryCount = retryCount,
                        additionalAvoidPatterns = attempts.flatMap { it.evaluation.aiLikePatterns }.normalizedAvoidPatterns(),
                    ),
                )
            }

            item.contentId to attempts
        }
    }

    private fun resolveBatchGeneration(
        contentItems: List<BatchContentItem>,
        inputKeywords: List<String>,
    ): BatchContentAnalysisResult =
        withModelFallback("generate batch auto content") { model ->
            val response = rateLimiterService.executeBatchAutoGeneration(inputKeywords, model, contentItems)
            parseBatchGenerationResponse(response?.text(), model)
        }

    private fun resolveBatchEvaluation(
        contentItems: List<BatchContentItem>,
        batchGeneration: BatchContentAnalysisResult,
    ): Map<String, BatchContentEvaluationItem> {
        val evaluationInputs =
            contentItems.mapNotNull { item ->
                batchGeneration.results[item.contentId]?.let { generated ->
                    BatchAutoContentEvaluationInput(
                        contentId = item.contentId,
                        content = item.content,
                        generatedSummary = generated.summary,
                        generatedHeadlines = generated.provocativeHeadlines,
                        retryCount = 0,
                    )
                }
            }

        if (evaluationInputs.isEmpty()) {
            logger.warn("No batch evaluation inputs were produced from batch generation results")
            return emptyMap()
        }

        val batchEvaluation =
            withModelFallback("evaluate batch auto content") { model ->
                val response = rateLimiterService.executeBatchAutoEvaluation(model, evaluationInputs)
                parseBatchEvaluationResponse(response?.text(), model)?.normalized()
            }

        return batchEvaluation.results
    }

    private fun buildBatchAttempt(
        item: BatchContentItem,
        batchGeneration: BatchContentAnalysisResult,
        batchEvaluation: Map<String, BatchContentEvaluationItem>,
    ): ContentAnalysisAttempt {
        val generated =
            batchGeneration.results[item.contentId]
                ?: throw AiProcessingException("Missing batch generation result for contentId=${item.contentId}")
        val evaluation =
            batchEvaluation[item.contentId]
                ?: throw AiProcessingException("Missing batch evaluation result for contentId=${item.contentId}")

        return ContentAnalysisAttempt(
            summary = generated.summary,
            provocativeHeadlines = generated.provocativeHeadlines,
            matchedKeywords = generated.matchedKeywords,
            evaluation =
                ContentEvaluationResult(
                    score = evaluation.score,
                    reason = evaluation.reason,
                    aiLikePatterns = evaluation.aiLikePatterns,
                    recommendedFix = evaluation.recommendedFix,
                    passed = evaluation.passed,
                    retryCount = evaluation.retryCount,
                    usedModel = batchGeneration.usedModel,
                ),
            generationModel = batchGeneration.usedModel,
        )
    }

    private fun persistAttempts(
        content: Content,
        attempts: List<ContentAnalysisAttempt>,
        generationMode: ContentGenerationMode,
    ): List<ContentGenerationAttempt> =
        attempts.mapIndexed { index, attempt ->
            contentGenerationAttemptRepository.save(
                ContentGenerationAttempt(
                    content = content,
                    generationMode = generationMode,
                    attemptNumber = index + 1,
                    model = buildModelLabel(attempt),
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

    private fun buildModelLabel(attempt: ContentAnalysisAttempt): String {
        val evaluationModel = attempt.evaluation.usedModel?.modelName
        val generationModel = attempt.generationModel?.modelName

        return when {
            generationModel != null && evaluationModel != null -> "$generationModel -> $evaluationModel"
            generationModel != null -> generationModel
            evaluationModel != null -> evaluationModel
            else -> "unknown"
        }
    }

    private fun parseAutoGenerationResponse(responseText: String?): AutoGeneratedContentDraft? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            AutoGeneratedContentDraft(
                summary = jsonResponse.stringValue("summary"),
                provocativeHeadlines = jsonResponse.stringList("provocativeHeadlines").singleHeadline(),
                matchedKeywords = jsonResponse.stringList("matchedKeywords"),
            )
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse auto generation response: $responseText", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error parsing auto generation response: $responseText", e)
            null
        }

    private fun parseBatchGenerationResponse(
        responseText: String?,
        model: GeminiModel,
    ): BatchContentAnalysisResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            val resultsArray = jsonResponse["results"] as? List<*> ?: emptyList<Any>()

            val resultsMap =
                resultsArray
                    .mapNotNull { obj ->
                        (obj as? Map<*, *>)?.let { map ->
                            val contentId = map["contentId"] as? String ?: return@let null
                            contentId to
                                BatchContentAnalysisItem(
                                    contentId = contentId,
                                    summary = map.stringValue("summary"),
                                    provocativeHeadlines = map.stringList("provocativeHeadlines").singleHeadline(),
                                    matchedKeywords = map.stringList("matchedKeywords"),
                                )
                        }
                    }.toMap()

            BatchContentAnalysisResult(results = resultsMap, usedModel = model)
        } catch (e: JsonSyntaxException) {
            logger.error("Failed to parse batch generation response: $responseText", e)
            null
        } catch (e: Exception) {
            logger.error("Unexpected error parsing batch generation response: $responseText", e)
            null
        }

    private fun parseEvaluationResponse(
        responseText: String?,
        model: GeminiModel,
    ): ContentEvaluationResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            ContentEvaluationResult(
                score = jsonResponse.intValue("score"),
                reason = jsonResponse.stringValue("reason"),
                aiLikePatterns = jsonResponse.stringList("aiLikePatterns"),
                recommendedFix = jsonResponse.stringValue("recommendedFix"),
                passed = jsonResponse.booleanValue("passed"),
                retryCount = jsonResponse.intValue("retryCount"),
                usedModel = model,
            )
        } catch (e: JsonSyntaxException) {
            recoverEvaluationResponse(responseText, model)?.also {
                logger.warn("Recovered malformed evaluation response for model: ${model.modelName}")
            } ?: run {
                logger.error("Failed to parse evaluation response: $responseText", e)
                null
            }
        } catch (e: Exception) {
            logger.error("Unexpected error parsing evaluation response: $responseText", e)
            null
        }

    private fun parseBatchEvaluationResponse(
        responseText: String?,
        model: GeminiModel,
    ): BatchContentEvaluationResult? =
        try {
            val jsonResponse = gson.fromJson(responseText, Map::class.java)
            val resultsArray = jsonResponse["results"] as? List<*> ?: emptyList<Any>()

            val resultsMap =
                resultsArray
                    .mapNotNull { obj ->
                        (obj as? Map<*, *>)?.let { map ->
                            val contentId = map["contentId"] as? String ?: return@let null
                            contentId to
                                BatchContentEvaluationItem(
                                    contentId = contentId,
                                    score = map.intValue("score"),
                                    reason = map.stringValue("reason"),
                                    aiLikePatterns = map.stringList("aiLikePatterns"),
                                    recommendedFix = map.stringValue("recommendedFix"),
                                    passed = map.booleanValue("passed"),
                                    retryCount = map.intValue("retryCount"),
                                )
                        }
                    }.toMap()

            BatchContentEvaluationResult(results = resultsMap, usedModel = model)
        } catch (e: JsonSyntaxException) {
            recoverBatchEvaluationResponse(responseText, model)?.also {
                logger.warn("Recovered malformed batch evaluation response for model: ${model.modelName}")
            } ?: run {
                logger.error("Failed to parse batch evaluation response: $responseText", e)
                null
            }
        } catch (e: Exception) {
            logger.error("Unexpected error parsing batch evaluation response: $responseText", e)
            null
        }

    private fun ContentAnalysisAttempt.toFinalResult(): ContentAnalysisResult =
        ContentAnalysisResult(
            summary = summary,
            provocativeHeadlines = provocativeHeadlines,
            matchedKeywords = matchedKeywords,
            qualityScore = evaluation.score,
            qualityReason = evaluation.reason,
            aiLikePatterns = evaluation.aiLikePatterns,
            recommendedFix = evaluation.recommendedFix,
            passed = evaluation.passed,
            retryCount = evaluation.retryCount,
            usedModel = generationModel ?: evaluation.usedModel,
        )

    private fun ContentEvaluationResult.normalized(retryCount: Int): ContentEvaluationResult {
        val normalizedScore = score.coerceIn(0, 10)
        return copy(
            score = normalizedScore,
            passed = normalizedScore >= contentAnalysisProperties.minNaturalnessScore,
            retryCount = retryCount,
        )
    }

    private fun BatchContentEvaluationResult.normalized(): BatchContentEvaluationResult =
        copy(
            results =
                results.mapValues { (_, item) ->
                    item.copy(
                        score = item.score.coerceIn(0, 10),
                        passed = item.score.coerceIn(0, 10) >= contentAnalysisProperties.minNaturalnessScore,
                    )
                },
        )

    private fun getReservedKeywordNames(): List<String> =
        reservedKeywordRepository
            .findAll()
            .map { it.name }
            .toList()

    private fun <T> withModelFallback(
        actionName: String,
        block: (GeminiModel) -> T?,
    ): T {
        val models = GeminiModel.entries
        var allFailedDueToRateLimit = true
        var lastRateLimitException: RateLimitExceededException? = null

        for (model in models) {
            try {
                logger.info("Trying to $actionName with model: ${model.modelName}")
                val result = block(model)
                if (result != null) {
                    logger.info("Successfully completed $actionName with model: ${model.modelName}")
                    return result
                }
                allFailedDueToRateLimit = false
                logger.warn("$actionName returned null for model: ${model.modelName}")
            } catch (e: RateLimitExceededException) {
                logger.warn("Rate limit exceeded for model ${model.modelName} during $actionName, trying next model")
                lastRateLimitException = e
            } catch (e: Exception) {
                logger.error("Error while trying to $actionName with model ${model.modelName}: ${e.message}", e)
                allFailedDueToRateLimit = false
            }
        }

        if (allFailedDueToRateLimit && lastRateLimitException != null) {
            logger.error("All models failed due to rate limit while trying to $actionName")
            throw lastRateLimitException
        }

        throw AiProcessingException("Failed to $actionName: all models failed")
    }

    private fun Map<*, *>.stringValue(key: String): String = this[key] as? String ?: ""

    private fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private fun Map<*, *>.intValue(key: String): Int = (this[key] as? Number)?.toInt() ?: 0

    private fun Map<*, *>.booleanValue(key: String): Boolean = this[key] as? Boolean ?: false

    private fun List<String>.singleHeadline(): List<String> = take(1)

    private fun List<String>.normalizedAvoidPatterns(): List<String> =
        map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(5)

    private fun recoverEvaluationResponse(
        responseText: String?,
        model: GeminiModel,
    ): ContentEvaluationResult? {
        val text = responseText?.trim().orEmpty()
        if (text.isEmpty()) {
            return null
        }

        val score = extractIntField(text, "score") ?: return null
        val reason = extractStringField(text, "reason") ?: "평가 응답 일부가 손상되어 기본 사유를 사용했습니다."
        val recommendedFix = extractStringField(text, "recommendedFix").orEmpty()
        val passed = extractBooleanField(text, "passed") ?: (score >= contentAnalysisProperties.minNaturalnessScore)
        val retryCount = extractIntField(text, "retryCount") ?: 0

        return ContentEvaluationResult(
            score = score,
            reason = reason,
            aiLikePatterns = extractStringArrayField(text, "aiLikePatterns"),
            recommendedFix = recommendedFix,
            passed = passed,
            retryCount = retryCount,
            usedModel = model,
        )
    }

    private fun recoverBatchEvaluationResponse(
        responseText: String?,
        model: GeminiModel,
    ): BatchContentEvaluationResult? {
        val text = responseText?.trim().orEmpty()
        if (text.isEmpty()) {
            return null
        }

        val results =
            extractBalancedJsonObjects(text)
                .mapNotNull { objectText ->
                    recoverBatchEvaluationItem(objectText)
                }.associateBy { it.contentId }

        return if (results.isEmpty()) {
            null
        } else {
            BatchContentEvaluationResult(results = results, usedModel = model)
        }
    }

    private fun recoverBatchEvaluationItem(objectText: String): BatchContentEvaluationItem? {
        val contentId = extractStringField(objectText, "contentId") ?: return null
        val score = extractIntField(objectText, "score") ?: return null

        return BatchContentEvaluationItem(
            contentId = contentId,
            score = score,
            reason = extractStringField(objectText, "reason") ?: "평가 응답 일부가 손상되어 기본 사유를 사용했습니다.",
            aiLikePatterns = extractStringArrayField(objectText, "aiLikePatterns"),
            recommendedFix = extractStringField(objectText, "recommendedFix").orEmpty(),
            passed = extractBooleanField(objectText, "passed") ?: (score >= contentAnalysisProperties.minNaturalnessScore),
            retryCount = extractIntField(objectText, "retryCount") ?: 0,
        )
    }

    private fun extractBalancedJsonObjects(text: String): List<String> {
        val arrayStart =
            text
                .indexOf("\"results\"")
                .takeIf { it >= 0 }
                ?.let { text.indexOf('[', it) }
                ?: text.indexOf('[')

        if (arrayStart < 0) {
            return emptyList()
        }

        val objects = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inString = false
        var escaped = false

        text.drop(arrayStart + 1).forEachIndexed { offset, char ->
            val index = arrayStart + 1 + offset
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> {
                    if (depth == 0) {
                        startIndex = index
                    }
                    depth++
                }
                char == '}' && depth > 0 -> {
                    depth--
                    if (depth == 0 && startIndex >= 0) {
                        objects.add(text.substring(startIndex, index + 1))
                        startIndex = -1
                    }
                }
                char == ']' && depth == 0 -> return objects
            }
        }

        return objects
    }

    private fun extractIntField(
        text: String,
        field: String,
    ): Int? =
        Regex(""""$field"\s*:\s*(-?\d+)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private fun extractBooleanField(
        text: String,
        field: String,
    ): Boolean? =
        Regex(""""$field"\s*:\s*(true|false)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toBooleanStrictOrNull()

    private fun extractStringField(
        text: String,
        field: String,
    ): String? =
        Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.let(::decodeJsonString)
            ?.trim()

    private fun extractStringArrayField(
        text: String,
        field: String,
    ): List<String> {
        val arrayContent =
            Regex(""""$field"\s*:\s*\[(.*?)]""", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(text)
                ?.groupValues
                ?.get(1)
                ?: return emptyList()

        return Regex(""""((?:\\.|[^"\\])*)"""", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(arrayContent)
            .map { decodeJsonString(it.groupValues[1]).trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun decodeJsonString(value: String): String =
        runCatching {
            gson.fromJson("\"$value\"", String::class.java)
        }.getOrElse {
            value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

    private data class AutoGeneratedContentDraft(
        val summary: String,
        val provocativeHeadlines: List<String>,
        val matchedKeywords: List<String>,
    )

    private data class GeneratedDraftWithModel(
        val draft: AutoGeneratedContentDraft,
        val model: GeminiModel,
    )

    private data class ContentAnalysisAttempt(
        val summary: String,
        val provocativeHeadlines: List<String>,
        val matchedKeywords: List<String>,
        val evaluation: ContentEvaluationResult,
        val generationModel: GeminiModel?,
    )
}
