package com.nexters.external.dto

/**
 * 배치 컨텐츠 분석 요청 항목
 * @property contentId 컨텐츠 ID
 * @property content 분석할 컨텐츠 내용
 */
data class BatchContentItem(
    val contentId: String,
    val content: String,
)

/**
 * 배치 컨텐츠 분석 결과 (단일 항목)
 * @property contentId 컨텐츠 ID
 * @property summary 요약
 * @property provocativeHeadlines 자극적인 헤드라인 목록
 * @property matchedKeywords 매칭된 키워드 목록
 * @property suggestedKeywords 제안된 키워드 목록
 * @property provocativeKeywords 자극적인 키워드 목록
 */
data class BatchContentAnalysisItem(
    val contentId: String,
    val summary: String,
    val provocativeHeadlines: List<String>,
    val matchedKeywords: List<String>,
    val suggestedKeywords: List<String>,
    val provocativeKeywords: List<String>,
)

/**
 * 배치 컨텐츠 분석 결과
 * @property results contentId를 키로 하는 분석 결과 맵
 * @property usedModel 사용된 Gemini 모델
 */
data class BatchContentAnalysisResult(
    val results: Map<String, BatchContentAnalysisItem>,
    val usedModel: GeminiModel?,
)
