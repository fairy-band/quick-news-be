package com.nexters.external.apiclient

import com.google.genai.types.Schema
import com.google.genai.types.Type
import org.springframework.stereotype.Component

@Component
class GeminiContentSchemaFactory {
    fun autoContentGeneration(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "summary" to Schema.builder().type(Type.Known.STRING).description("콘텐츠 요약").build(),
                    "provocativeHeadlines" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .description("사람이 쓴 듯 자연스러운 헤드라인 후보")
                            .build(),
                    "matchedKeywords" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .description("요청 키워드 중 실제 관련 있는 키워드")
                            .build(),
                ),
            ).required(listOf("summary", "provocativeHeadlines", "matchedKeywords"))
            .build()

    fun batchAutoContentGeneration(): Schema =
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

    fun legacyKeywordDiscovery(): Schema =
        Schema
            .builder()
            .type(Type.Known.OBJECT)
            .properties(
                mapOf(
                    "matchedKeywords" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .build(),
                    "suggestedKeywords" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .build(),
                    "provocativeKeywords" to
                        Schema
                            .builder()
                            .type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING).build())
                            .build(),
                ),
            ).required(listOf("matchedKeywords", "suggestedKeywords", "provocativeKeywords"))
            .build()

    fun contentEvaluation(): Schema =
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

    fun batchContentEvaluation(): Schema =
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
