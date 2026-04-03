package com.nexters.external.service

import com.nexters.external.dto.AutoContentEvaluationInput
import com.nexters.external.dto.BatchAutoContentEvaluationInput
import com.nexters.external.dto.BatchContentItem
import com.nexters.external.dto.ContentEvaluationResult
import org.springframework.stereotype.Component

@Component
class ContentAnalysisPromptFactory {
    val autoGenerationPromptVersion: String = AUTO_GENERATION_PROMPT_VERSION
    val autoEvaluationPromptVersion: String = AUTO_EVALUATION_PROMPT_VERSION

    fun buildAutoContentGenerationPrompt(
        inputKeywords: List<String>,
        content: String,
    ): String =
        """
        당신은 기술 뉴스레터 편집자입니다. 아래 콘텐츠를 읽고, 사람이 실제로 쓴 것처럼 자연스러운 한글 요약과 헤드라인을 만들어 JSON으로만 반환하세요.

        콘텐츠:
        $content

        요청 키워드: ${inputKeywords.joinToString(", ")}

        공통 규칙:
        - 반드시 순수 JSON만 반환
        - 영어 원문이라면 결과는 자연스러운 한글로 번역해서 작성
        - 내용에 없는 과장, 추측, 허위 단정 금지
        - 헤드라인은 클릭을 유도하되 기계적으로 과장된 표현은 피할 것

        결과 규칙:
        - summary: 핵심 맥락과 왜 중요한지를 포함한 5-6문장 한글 요약
        - provocativeHeadlines: 최대 5개, 가장 좋은 후보부터 정렬
        - matchedKeywords: 요청 키워드 중 실제로 관련 있는 항목만 최대 5개

        헤드라인 작성 규칙:
        - 22-38자 내외의 완결된 한국어 문장
        - 실제 에디터가 제목을 다듬은 듯한 리듬과 어휘 사용
        - '정체', '충격', '경악', '무조건', '소름', '역대급', '절대', '비밀 공개' 같은 상투적 클릭베이트는 피할 것
        - 정보의 핵심 대상을 드러내되, 남발된 광고 문구처럼 보이지 않게 쓸 것

        권장 헤드라인 예시:
        - "오픈AI가 응답 속도를 줄인 대신 정확도를 택한 이유"
        - "도커 없이 로컬 개발환경을 맞춘 팀의 현실적인 선택"
        - "루비에서 타입 검사를 붙였더니 배포 사고가 줄었다"
        - "이 팀이 Redis 락 대신 작업 큐를 다시 고른 배경"
        - "파이어폭스가 웹GPU 디버깅 도구를 먼저 챙긴 이유"
        - "잘 만든 마이그레이션이 장애 대응 시간을 줄이는 방식"

        권장하지 않는 헤드라인 예시:
        - "개발자들이 충격받은 최신 기술의 정체"
        - "이 방법 모르면 아직도 하수 개발자입니다"
        - "AI도 인정한 역대급 백엔드 최적화 비밀 공개"
        - "당장 안 보면 손해 보는 쿠버네티스 반전 팁"
        - "실리콘밸리만 안다는 무조건 성공하는 아키텍처"

        형식:
        {"summary":"요약","provocativeHeadlines":["헤드라인1"],"matchedKeywords":["키워드1"]}
        """.trimIndent()

    fun buildBatchAutoContentGenerationPrompt(
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

        return """
            당신은 기술 뉴스레터 편집자입니다. 아래 ${contentItems.size}개의 콘텐츠를 각각 읽고, 사람이 실제로 쓴 것처럼 자연스러운 한글 요약과 헤드라인을 만들어 JSON으로만 반환하세요.

            요청 키워드: ${inputKeywords.joinToString(", ")}

            콘텐츠 목록:
            $contentsSection

            공통 규칙:
            - 반드시 순수 JSON만 반환
            - 영어 원문이라면 결과는 자연스러운 한글로 번역해서 작성
            - 각 콘텐츠는 독립적으로 판단하고 반드시 contentId를 유지
            - 내용에 없는 과장, 추측, 허위 단정 금지

            각 콘텐츠 결과 규칙:
            - summary: 핵심 맥락과 왜 중요한지를 포함한 5-6문장 한글 요약
            - provocativeHeadlines: 최대 5개, 가장 좋은 후보부터 정렬
            - matchedKeywords: 요청 키워드 중 실제로 관련 있는 항목만 최대 5개

            헤드라인 작성 규칙:
            - 22-38자 내외의 완결된 한국어 문장
            - 실제 에디터가 제목을 다듬은 듯한 리듬과 어휘 사용
            - '정체', '충격', '경악', '무조건', '소름', '역대급', '절대', '비밀 공개' 같은 상투적 클릭베이트는 피할 것
            - 정보의 핵심 대상을 드러내되, 남발된 광고 문구처럼 보이지 않게 쓸 것

            권장 헤드라인 예시:
            - "오픈AI가 응답 속도를 줄인 대신 정확도를 택한 이유"
            - "도커 없이 로컬 개발환경을 맞춘 팀의 현실적인 선택"
            - "루비에서 타입 검사를 붙였더니 배포 사고가 줄었다"
            - "이 팀이 Redis 락 대신 작업 큐를 다시 고른 배경"
            - "파이어폭스가 웹GPU 디버깅 도구를 먼저 챙긴 이유"
            - "잘 만든 마이그레이션이 장애 대응 시간을 줄이는 방식"

            권장하지 않는 헤드라인 예시:
            - "개발자들이 충격받은 최신 기술의 정체"
            - "이 방법 모르면 아직도 하수 개발자입니다"
            - "AI도 인정한 역대급 백엔드 최적화 비밀 공개"
            - "당장 안 보면 손해 보는 쿠버네티스 반전 팁"
            - "실리콘밸리만 안다는 무조건 성공하는 아키텍처"

            형식:
            {"results":[{"contentId":"ID1","summary":"요약","provocativeHeadlines":["헤드라인1"],"matchedKeywords":["키워드1"]}]}
            """.trimIndent()
    }

    fun buildLegacyKeywordDiscoveryPrompt(
        inputKeywords: List<String>,
        content: String,
    ): String =
        """
        다음 콘텐츠에서 키워드만 추출해 JSON으로 반환하세요.

        콘텐츠:
        $content

        요청 키워드: ${inputKeywords.joinToString(", ")}

        규칙:
        - 반드시 순수 JSON만 반환
        - 영어 원문이어도 키워드는 자연스러운 한글 또는 고유명사 원형으로 정리
        - matchedKeywords: 요청 키워드 중 실제 관련 있는 항목만 최대 5개
        - suggestedKeywords: 콘텐츠 기반 추천 키워드 최대 5개
        - provocativeKeywords: 클릭을 끌 수 있지만 과도하게 저급하지 않은 키워드 최대 3개

        형식:
        {"matchedKeywords":["키워드1"],"suggestedKeywords":["키워드2"],"provocativeKeywords":["키워드3"]}
        """.trimIndent()

    fun buildAutoContentEvaluationPrompt(input: AutoContentEvaluationInput): String =
        """
        당신은 기술 뉴스레터 편집장의 품질 검수자입니다. 아래 생성 결과가 얼마나 인간 편집자처럼 자연스러운지 평가하고 JSON으로만 반환하세요.

        원문 콘텐츠:
        ${input.content}

        생성된 요약:
        ${input.generatedSummary}

        생성된 헤드라인:
        ${input.generatedHeadlines.joinToString(separator = "\n") { "- $it" }}

        현재 재시도 횟수: ${input.retryCount}

        평가 기준:
        - 0점은 매우 기계적이고 상투적임, 10점은 사람이 실제로 다듬은 제목처럼 자연스러움
        - 기술 맥락을 제대로 반영하는지
        - 과장된 광고 문구, 뻔한 AI 어휘, 억지 클릭베이트가 있는지
        - 문장 리듬이 어색하거나 너무 판에 박힌 표현인지

        반환 규칙:
        - 반드시 순수 JSON만 반환
        - score: 0-10 정수
        - reason: 점수를 준 핵심 이유를 한글 1-2문장으로 설명
        - aiLikePatterns: 어색하거나 AI스럽게 느껴진 표현 패턴 목록
        - recommendedFix: 다시 생성한다면 어떤 방향으로 고쳐야 하는지 한글 1문장
        - passed: score가 7 이상이면 true, 아니면 false
        - retryCount: 입력받은 현재 재시도 횟수를 그대로 반환

        형식:
        {"score":7,"reason":"설명","aiLikePatterns":["패턴1"],"recommendedFix":"수정 방향","passed":true,"retryCount":0}
        """.trimIndent()

    fun buildBatchAutoContentEvaluationPrompt(items: List<BatchAutoContentEvaluationInput>): String {
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

        return """
            당신은 기술 뉴스레터 편집장의 품질 검수자입니다. 아래 각 생성 결과가 얼마나 인간 편집자처럼 자연스러운지 평가하고 JSON으로만 반환하세요.

            평가 기준:
            - 0점은 매우 기계적이고 상투적임, 10점은 사람이 실제로 다듬은 제목처럼 자연스러움
            - 기술 맥락 반영 여부
            - 과장된 광고 문구, 뻔한 AI 어휘, 억지 클릭베이트 여부
            - 문장 리듬과 편집 품질

            콘텐츠 목록:
            $itemsSection

            반환 규칙:
            - 반드시 순수 JSON만 반환
            - score: 0-10 정수
            - passed: score가 7 이상이면 true, 아니면 false
            - retryCount: 각 항목의 입력값을 그대로 반환
            - 모든 항목은 contentId를 유지

            형식:
            {"results":[{"contentId":"ID1","score":7,"reason":"설명","aiLikePatterns":["패턴1"],"recommendedFix":"수정 방향","passed":true,"retryCount":0}]}
            """.trimIndent()
    }

    fun buildPromptRevisionSuggestionPrompt(
        originalPrompt: String,
        evaluationResult: ContentEvaluationResult,
    ): String =
        """
        당신은 프롬프트 엔지니어입니다. 아래 두 입력만 보고 1번 프롬프트의 수정안을 제안하세요.

        [입력 1: 기존 생성 프롬프트]
        $originalPrompt

        [입력 2: 평가 결과(JSON)]
        {"score":${evaluationResult.score},"reason":"${escapeJson(evaluationResult.reason)}","aiLikePatterns":${toJsonArray(evaluationResult.aiLikePatterns)},"recommendedFix":"${escapeJson(evaluationResult.recommendedFix)}","passed":${evaluationResult.passed},"retryCount":${evaluationResult.retryCount}}

        출력 규칙:
        - 일반 텍스트로만 반환
        - 먼저 왜 수정이 필요한지 3문장 이내로 설명
        - 그 다음 실제로 교체 가능한 프롬프트 수정안을 제시
        - 입력으로 받은 두 정보 외 가정은 최소화
        """.trimIndent()

    private fun toJsonArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    companion object {
        private const val AUTO_GENERATION_PROMPT_VERSION = "auto-generation-v2-human-like"
        private const val AUTO_EVALUATION_PROMPT_VERSION = "auto-evaluation-v1"
    }
}
