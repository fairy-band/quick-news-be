package com.nexters.admin.dto

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 개선된 콘텐츠 응답 with 요약/노출 상태
 */
data class ContentDetailResponse(
    val id: Long,
    val basicInfo: BasicInfo,
    val contentData: ContentData,
    val status: ContentStatus,
    val timestamps: Timestamps
) {
    data class BasicInfo(
        val newsletterSourceId: String?,
        val newsletterName: String,
        val publishedAt: LocalDate
    )

    data class ContentData(
        val title: String,
        val content: String,
        val originalUrl: String,
        val preview: String = content.take(200) + if (content.length > 200) "..." else ""
    )

    data class ContentStatus(
        val hasSummary: Boolean,
        val isExposed: Boolean,
        val summaryCount: Int = 0,
        val keywordCount: Int = 0
    )

    data class Timestamps(
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    )
}

/**
 * 페이지네이션된 콘텐츠 리스트 응답
 */
data class ContentListResponse<T>(
    val metadata: ListMetadata,
    val contents: List<T>,
    val pagination: PaginationDetails
) {
    data class ListMetadata(
        val totalElements: Long,
        val appliedFilters: Map<String, String> = emptyMap(),
        val sortBy: String = "publishedAt"
    )

    data class PaginationDetails(
        val currentPage: Int,
        val pageSize: Int,
        val totalPages: Int,
        val hasNext: Boolean,
        val hasPrevious: Boolean
    )
}

/**
 * 키워드 매칭 응답
 */
data class KeywordMatchResponse(
    val success: Boolean,
    val summary: MatchSummary,
    val keywords: KeywordDetails,
    val suggestions: SuggestionDetails,
    val message: String
) {
    data class MatchSummary(
        val totalReserved: Int,
        val matched: Int,
        val added: Int,
        val alreadyExisting: Int
    )

    data class KeywordDetails(
        val matched: List<String>,
        val added: List<String>,
        val existing: List<String>,
        val provocative: List<String>
    )

    data class SuggestionDetails(
        val suggested: List<String>,
        val relevanceScore: Map<String, Double> = emptyMap()
    )
}
