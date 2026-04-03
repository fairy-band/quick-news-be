package com.nexters.external.apiclient

import com.google.genai.Client
import com.google.genai.errors.ClientException
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Schema
import com.nexters.external.dto.AutoContentEvaluationInput
import com.nexters.external.dto.BatchAutoContentEvaluationInput
import com.nexters.external.dto.BatchContentItem
import com.nexters.external.dto.ContentEvaluationResult
import com.nexters.external.dto.GeminiModel
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.service.ContentAnalysisPromptFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GeminiClient(
    @Value("\${ai.gemini.key}") private val apiKey: String,
    private val promptFactory: ContentAnalysisPromptFactory,
    private val schemaFactory: GeminiContentSchemaFactory,
) {
    private val logger = LoggerFactory.getLogger(GeminiClient::class.java)

    private val client: Client =
        Client
            .builder()
            .apiKey(apiKey)
            .build()

    fun requestAutoContentGeneration(
        inputKeywords: List<String>,
        model: GeminiModel,
        content: String,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = promptFactory.buildAutoContentGenerationPrompt(inputKeywords, content),
            maxOutputTokens = 3000,
            schema = schemaFactory.autoContentGeneration(),
        )

    fun requestBatchAutoContentGeneration(
        inputKeywords: List<String>,
        model: GeminiModel,
        contentItems: List<BatchContentItem>,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = promptFactory.buildBatchAutoContentGenerationPrompt(inputKeywords, contentItems),
            maxOutputTokens = 8000,
            schema = schemaFactory.batchAutoContentGeneration(),
        )

    fun requestLegacyKeywordDiscovery(
        inputKeywords: List<String>,
        model: GeminiModel,
        content: String,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = promptFactory.buildLegacyKeywordDiscoveryPrompt(inputKeywords, content),
            maxOutputTokens = 2000,
            schema = schemaFactory.legacyKeywordDiscovery(),
        )

    fun requestAutoContentEvaluation(
        model: GeminiModel,
        input: AutoContentEvaluationInput,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = promptFactory.buildAutoContentEvaluationPrompt(input),
            maxOutputTokens = 1500,
            schema = schemaFactory.contentEvaluation(),
        )

    fun requestBatchAutoContentEvaluation(
        model: GeminiModel,
        items: List<BatchAutoContentEvaluationInput>,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = promptFactory.buildBatchAutoContentEvaluationPrompt(items),
            maxOutputTokens = 4000,
            schema = schemaFactory.batchContentEvaluation(),
        )

    private fun executeJsonRequest(
        model: GeminiModel,
        prompt: String,
        maxOutputTokens: Int,
        schema: Schema,
    ): GenerateContentResponse? =
        try {
            client.models.generateContent(
                model.modelName,
                prompt,
                GenerateContentConfig
                    .builder()
                    .temperature(0.4f)
                    .maxOutputTokens(maxOutputTokens)
                    .topP(0.8f)
                    .topK(40f)
                    .candidateCount(1)
                    .responseMimeType("application/json")
                    .responseSchema(schema)
                    .build(),
            )
        } catch (e: ClientException) {
            throwIfRateLimited(model, e)
            logger.error("Model ${model.modelName} failed with ClientException: ${e.message}", e)
            null
        } catch (e: Exception) {
            logger.error("Model ${model.modelName} failed with exception: ${e.message}", e)
            null
        }

    private fun executeTextRequest(
        model: GeminiModel,
        prompt: String,
        maxOutputTokens: Int,
    ): GenerateContentResponse? =
        try {
            client.models.generateContent(
                model.modelName,
                prompt,
                GenerateContentConfig
                    .builder()
                    .temperature(0.3f)
                    .maxOutputTokens(maxOutputTokens)
                    .topP(0.8f)
                    .topK(40f)
                    .candidateCount(1)
                    .build(),
            )
        } catch (e: ClientException) {
            throwIfRateLimited(model, e)
            logger.error("Model ${model.modelName} failed with ClientException: ${e.message}", e)
            null
        } catch (e: Exception) {
            logger.error("Model ${model.modelName} failed with exception: ${e.message}", e)
            null
        }

    private fun throwIfRateLimited(
        model: GeminiModel,
        exception: ClientException,
    ) {
        if (exception.message?.contains("429") == true || exception.message?.contains("Too Many Requests") == true) {
            logger.warn("Rate limit (429) exceeded for model ${model.modelName}")
            throw RateLimitExceededException(
                "Rate limit exceeded: 429 Too Many Requests for model ${model.modelName}",
                "API_RATE_LIMIT",
                model.modelName,
            )
        }
    }
}
