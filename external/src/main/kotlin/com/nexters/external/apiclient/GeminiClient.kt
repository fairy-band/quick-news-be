package com.nexters.external.apiclient

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Schema
import com.google.genai.types.Type
import com.nexters.external.dto.GeminiModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GeminiClient(
    @Value("\${ai.gemini.key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(GeminiClient::class.java)

    private val client: Client =
        Client
            .builder()
            .apiKey(apiKey)
            .build()

    fun requestKeywords(
        inputKeywords: List<String>,
        model: GeminiModel,
        content: String
    ): GenerateContentResponse? { // 제공된 토큰을 초과해서 생성할 경우 null을 반환함.
        val prompt = buildKeywordAnalysisPrompt(inputKeywords, content)

        return try {
            client.models.generateContent(
                model.modelName,
                prompt,
                GenerateContentConfig
                    .builder()
                    .temperature(0.3f) // 창의성
                    .maxOutputTokens(2000)
                    .topP(0.8f)
                    .topK(40f)
                    .candidateCount(1)
                    .responseMimeType("application/json")
                    .responseSchema(KEYWORD_ANALYSIS_SCHEMA)
                    .build(),
            )
        } catch (e: Exception) {
            logger.error("Model ${model.modelName} failed with exception: ${e.message}", e)
            null
        }
    }

    fun requestSummary(
        model: GeminiModel,
        content: String
    ): GenerateContentResponse? { // 제공된 토큰을 초과해서 생성할 경우 null을 반환함.
        val prompt = buildSummaryPrompt(content)

        return try {
            client.models.generateContent(
                model.modelName,
                prompt,
                GenerateContentConfig
                    .builder()
                    .temperature(0.4f) // 창의성
                    .maxOutputTokens(1500)
                    .topP(0.8f)
                    .topK(40f)
                    .candidateCount(1)
                    .responseMimeType("application/json")
                    .responseSchema(SUMMARY_SCHEMA)
                    .build(),
            )
        } catch (e: Exception) {
            logger.error("Model ${model.modelName} failed with exception: ${e.message}", e)
            null
        }
    }

    private fun buildKeywordAnalysisPrompt(
        inputKeywords: List<String>,
        content: String
    ): String =
        """
        콘텐츠에서 키워드를 추출하여 JSON으로 반환하세요.

        콘텐츠: $content
        요청 키워드: ${inputKeywords.joinToString(", ")}

        규칙:
        - 순수 JSON만 반환 (마크다운 금지)
        - 영어 콘텐츠의 경우 모든 키워드를 한글로 번역하여 반환
        - matchedKeywords: 요청한 키워드 중에서 콘텐츠와 일치하는 키워드만 반환 (최대 5개)
        - suggestedKeywords: 콘텐츠를 기반으로 새로 제안하는 키워드들 (최대 5개)
        - provocativeKeywords: 재미있고 자극적인 클릭베이트 키워드들 (최대 3개)
        - 간단하고 핵심적인 키워드만

        형식: {"matchedKeywords":["키워드1"],"suggestedKeywords":["키워드2"],"provocativeKeywords":["대박급키워드1"]}
        """.trimIndent()

    private fun buildSummaryPrompt(content: String): String =
        """
        다음 콘텐츠의 핵심 내용을 요약하고 클릭을 유도하는 자극적인 헤드라인을 추천하여 JSON으로 반환하세요.
        헤드라인에는 가급적 소프트웨어 엔지니어가 관심있을만한 기술적인 키워드를 포함시켜주세요.

        콘텐츠: $content

        규칙:
        - 순수 JSON만 반환 (마크다운 금지)
        - 영어 콘텐츠의 경우 모든 내용을 한글로 번역하여 요약 및 헤드라인 생성
        - 원본 텍스트를 직접 인용하지 말고 핵심 내용만 요약
        - summary: 콘텐츠의 주요 내용을 5-6문장으로 간결하게 요약 (한글로)
        - provocativeHeadlines: 클릭을 유도하는 자극적이고 흥미로운 헤드라인 (최대 5개, 한글로, 자극적인 순서대로 정렬)
        - 헤드라인은 호기심을 자극하고 클릭하고 싶게 만드는 완전한 문장으로 구성, 최대한 인간스럽게
        - 각 헤드라인은 15-25자 내외로 작성

        개발 관련 자극적인 헤드라인 예시:
        - "구글이 절대 알려주지 않는 최적화 기법"
        - "10년차 개발자도 모르는 치명적 실수"
        - "이것만 알면 당신도 시니어 개발자"
        - "페이스북이 인수하려던 그 기술의 정체"
        - "개발자 99%가 틀리는 면접 질문"
        - "아무도 모르는 AWS 비용 절감 꿀팁"
        - "넷플릭스가 쓰는 극비 아키텍처 공개"

        형식: {"summary":"핵심내용요약","provocativeHeadlines":["실리콘밸리 개발자들이 숨기는 비밀","구글이 절대 알려주지 않는 최적화 기법"]}
        """.trimIndent()

    companion object {
        private val KEYWORD_ANALYSIS_SCHEMA =
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
                                .description("요청된 키워드 중 콘텐츠와 일치하는 키워드들")
                                .build(),
                        "suggestedKeywords" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .description("콘텐츠 기반으로 새로 제안하는 키워드들")
                                .build(),
                        "provocativeKeywords" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .description("사람들의 이목을 끌 수 있는 자극적이고 흥미로운 키워드들")
                                .build(),
                    ),
                ).required(listOf("matchedKeywords", "suggestedKeywords", "provocativeKeywords"))
                .build()

        private val SUMMARY_SCHEMA =
            Schema
                .builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "summary" to
                            Schema
                                .builder()
                                .type(Type.Known.STRING)
                                .description("콘텐츠의 주요 내용을 요약한 문장")
                                .build(),
                        "provocativeHeadlines" to
                            Schema
                                .builder()
                                .type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build())
                                .description("사람들의 클릭을 유도할 수 있는 자극적이고 흥미로운 헤드라인")
                                .build(),
                    ),
                ).required(listOf("summary", "provocativeHeadlines"))
                .build()
    }
}
