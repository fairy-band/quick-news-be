package com.nexters.external.apiclient

import com.google.genai.Client
import com.google.genai.errors.ClientException
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Schema
import com.google.genai.types.Type
import com.nexters.external.dto.AutoContentEvaluationInput
import com.nexters.external.dto.BatchAutoContentEvaluationInput
import com.nexters.external.dto.BatchContentItem
import com.nexters.external.dto.ContentEvaluationResult
import com.nexters.external.dto.GeminiModel
import com.nexters.external.exception.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class GeminiClient(
    @param:Value("\${ai.gemini.key}") private val apiKey: String,
) {
    companion object {
        const val AUTO_GENERATION_PROMPT_VERSION: String = "auto-generation-v2-human-like"
        const val AUTO_EVALUATION_PROMPT_VERSION: String = "auto-evaluation-v1"

        private val AUTO_CONTENT_GENERATION_TEMPLATE = loadPromptTemplate("auto-content-generation.txt")
        private val BATCH_AUTO_CONTENT_GENERATION_TEMPLATE = loadPromptTemplate("batch-auto-content-generation.txt")
        private val AUTO_CONTENT_EVALUATION_TEMPLATE = loadPromptTemplate("auto-content-evaluation.txt")
        private val BATCH_AUTO_CONTENT_EVALUATION_TEMPLATE = loadPromptTemplate("batch-auto-content-evaluation.txt")
        private val PROMPT_REVISION_SUGGESTION_TEMPLATE = loadPromptTemplate("prompt-revision-suggestion.txt")

        private fun loadPromptTemplate(fileName: String): String =
            requireNotNull(GeminiClient::class.java.classLoader.getResourceAsStream("prompts/$fileName")) {
                "Prompt template not found: prompts/$fileName"
            }.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        private val AUTO_CONTENT_GENERATION_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "summary" to Schema.builder().type(Type.Known.STRING).build(),
                        "provocativeHeadlines" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                        "matchedKeywords" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                    ),
                ).required(listOf("summary", "provocativeHeadlines", "matchedKeywords"))
                .build()

        private val BATCH_AUTO_CONTENT_GENERATION_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "results" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(
                                    Schema
                                        .builder()
                                        .type(Type.Known.OBJECT)
                                        .properties(
                                            mapOf(
                                                "contentId" to Schema.builder().type(Type.Known.STRING).build(),
                                                "summary" to Schema.builder().type(Type.Known.STRING).build(),
                                                "provocativeHeadlines" to
                                                    Schema
                                                        .builder()
                                                        .type(Type.Known.ARRAY)
                                                        .items(Schema.builder().type(Type.Known.STRING).build())
                                                        .build(),
                                                "matchedKeywords" to
                                                    Schema
                                                        .builder()
                                                        .type(Type.Known.ARRAY)
                                                        .items(Schema.builder().type(Type.Known.STRING).build())
                                                        .build(),
                                            ),
                                        ).required(listOf("contentId", "summary", "provocativeHeadlines", "matchedKeywords"))
                                        .build(),
                                ).build(),
                    ),
                ).required(listOf("results"))
                .build()

        private val CONTENT_EVALUATION_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "score" to Schema.builder().type(Type.Known.INTEGER).build(),
                        "reason" to Schema.builder().type(Type.Known.STRING).build(),
                        "aiLikePatterns" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .build(),
                        "recommendedFix" to Schema.builder().type(Type.Known.STRING).build(),
                        "passed" to Schema.builder().type(Type.Known.BOOLEAN).build(),
                        "retryCount" to Schema.builder().type(Type.Known.INTEGER).build(),
                    ),
                ).required(listOf("score", "reason", "aiLikePatterns", "recommendedFix", "passed", "retryCount"))
                .build()

        private val BATCH_CONTENT_EVALUATION_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "results" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(
                                    Schema
                                        .builder()
                                        .type(Type.Known.OBJECT)
                                        .properties(
                                            mapOf(
                                                "contentId" to Schema.builder().type(Type.Known.STRING).build(),
                                                "score" to Schema.builder().type(Type.Known.INTEGER).build(),
                                                "reason" to Schema.builder().type(Type.Known.STRING).build(),
                                                "aiLikePatterns" to
                                                    Schema
                                                        .builder()
                                                        .type(Type.Known.ARRAY)
                                                        .items(Schema.builder().type(Type.Known.STRING).build())
                                                        .build(),
                                                "recommendedFix" to Schema.builder().type(Type.Known.STRING).build(),
                                                "passed" to Schema.builder().type(Type.Known.BOOLEAN).build(),
                                                "retryCount" to Schema.builder().type(Type.Known.INTEGER).build(),
                                            ),
                                        ).required(
                                            listOf(
                                                "contentId",
                                                "score",
                                                "reason",
                                                "aiLikePatterns",
                                                "recommendedFix",
                                                "passed",
                                                "retryCount",
                                            ),
                                        ).build(),
                                ).build(),
                    ),
                ).required(listOf("results"))
                .build()
    }

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
        additionalAvoidPatterns: List<String> = emptyList(),
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = buildAutoContentGenerationPrompt(inputKeywords, content, additionalAvoidPatterns),
            maxOutputTokens = 3000,
            schema = AUTO_CONTENT_GENERATION_SCHEMA,
        )

    fun requestBatchAutoContentGeneration(
        inputKeywords: List<String>,
        model: GeminiModel,
        contentItems: List<BatchContentItem>,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = buildBatchAutoContentGenerationPrompt(inputKeywords, contentItems),
            maxOutputTokens = 8000,
            schema = BATCH_AUTO_CONTENT_GENERATION_SCHEMA,
        )

    fun requestAutoContentEvaluation(
        model: GeminiModel,
        input: AutoContentEvaluationInput,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = buildAutoContentEvaluationPrompt(input),
            maxOutputTokens = 1500,
            schema = CONTENT_EVALUATION_SCHEMA,
        )

    fun requestBatchAutoContentEvaluation(
        model: GeminiModel,
        items: List<BatchAutoContentEvaluationInput>,
    ): GenerateContentResponse? =
        executeJsonRequest(
            model = model,
            prompt = buildBatchAutoContentEvaluationPrompt(items),
            maxOutputTokens = 4000,
            schema = BATCH_CONTENT_EVALUATION_SCHEMA,
        )

    fun requestPromptRevisionSuggestion(
        model: GeminiModel,
        originalPrompt: String,
        evaluationResult: ContentEvaluationResult,
    ): GenerateContentResponse? =
        executeTextRequest(
            model = model,
            prompt = buildPromptRevisionSuggestionPrompt(originalPrompt, evaluationResult),
            maxOutputTokens = 3000,
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

    private fun buildAutoContentGenerationPrompt(
        inputKeywords: List<String>,
        content: String,
        additionalAvoidPatterns: List<String>,
    ): String =
        renderPrompt(
            AUTO_CONTENT_GENERATION_TEMPLATE,
            "CONTENT" to content,
            "REQUEST_KEYWORDS" to inputKeywords.joinToString(", "),
            "DYNAMIC_AVOID_PATTERNS_SECTION" to buildDynamicAvoidPatternsSection(additionalAvoidPatterns),
        )

    private fun buildBatchAutoContentGenerationPrompt(
        inputKeywords: List<String>,
        contentItems: List<BatchContentItem>,
    ): String {
        val contentsSection =
            contentItems.joinToString("\n\n") { item ->
                """
                [콘텐츠 ID: ${item.contentId}]
                ${item.content}
                """.trimIndent()
            }

        return renderPrompt(
            BATCH_AUTO_CONTENT_GENERATION_TEMPLATE,
            "CONTENT_COUNT" to contentItems.size.toString(),
            "REQUEST_KEYWORDS" to inputKeywords.joinToString(", "),
            "CONTENTS_SECTION" to contentsSection,
        )
    }

    private fun buildAutoContentEvaluationPrompt(input: AutoContentEvaluationInput): String =
        renderPrompt(
            AUTO_CONTENT_EVALUATION_TEMPLATE,
            "CONTENT" to input.content,
            "GENERATED_SUMMARY" to input.generatedSummary,
            "GENERATED_HEADLINES" to input.generatedHeadlines.joinToString(separator = "\n") { "- $it" },
            "RETRY_COUNT" to input.retryCount.toString(),
        )

    private fun buildDynamicAvoidPatternsSection(additionalAvoidPatterns: List<String>): String {
        val patterns =
            additionalAvoidPatterns
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .take(5)

        if (patterns.isEmpty()) {
            return ""
        }

        val bulletPoints = patterns.joinToString("\n") { """- "$it"처럼 보이는 표현이나 말투는 이번 재작성에서 피할 것""" }

        return """
            추가 금지 패턴:
            - 아래 항목들은 이전 검수에서 AI스럽거나 클릭베이트처럼 보인다고 지적된 패턴임
            $bulletPoints
            """.trimIndent()
    }

    private fun buildBatchAutoContentEvaluationPrompt(items: List<BatchAutoContentEvaluationInput>): String {
        val itemsSection =
            items.joinToString("\n\n") { item ->
                """
                [콘텐츠 ID: ${item.contentId}]
                원문 콘텐츠:
                ${item.content}

                생성된 요약:
                ${item.generatedSummary}

                생성된 헤드라인:
                ${item.generatedHeadlines.joinToString(separator = "\n") { "- $it" }}

                현재 재시도 횟수: ${item.retryCount}
                """.trimIndent()
            }

        return renderPrompt(
            BATCH_AUTO_CONTENT_EVALUATION_TEMPLATE,
            "ITEMS_SECTION" to itemsSection,
        )
    }

    private fun buildPromptRevisionSuggestionPrompt(
        originalPrompt: String,
        evaluationResult: ContentEvaluationResult,
    ): String =
        renderPrompt(
            PROMPT_REVISION_SUGGESTION_TEMPLATE,
            "ORIGINAL_PROMPT" to originalPrompt,
            "EVALUATION_RESULT_JSON" to buildEvaluationResultJson(evaluationResult),
        )

    private fun renderPrompt(
        template: String,
        vararg replacements: Pair<String, String>,
    ): String =
        replacements
            .fold(template) { rendered, (key, value) ->
                rendered.replace("{{$key}}", value)
            }.trimIndent()

    private fun buildEvaluationResultJson(evaluationResult: ContentEvaluationResult): String =
        """{"score":${evaluationResult.score},"reason":"${escapeJson(
            evaluationResult.reason
        )}","aiLikePatterns":${toJsonArray(
            evaluationResult.aiLikePatterns
        )},"recommendedFix":"${escapeJson(
            evaluationResult.recommendedFix
        )}","passed":${evaluationResult.passed},"retryCount":${evaluationResult.retryCount}}"""

    private fun toJsonArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
