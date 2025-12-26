package com.nexters.api.dto

import com.nexters.api.enums.Language
import java.time.LocalDateTime

/**
 * 개선된 노출 콘텐츠 목록 응답 (무한 스크롤 페이지네이션)
 */
data class ExposureContentListApiResponse(
    val metadata: ListMetadata,
    val contents: List<ExposureContentApiResponse>,
    val pagination: PaginationInfo
) {
    data class ListMetadata(
        val timestamp: LocalDateTime = LocalDateTime.now(),
        val version: String = "2.0",
        val totalFetched: Int
    )

    data class PaginationInfo(
        val hasMore: Boolean,
        val nextOffset: Long?,
        val currentSize: Int
    )
}

/**
 * 개선된 노출 콘텐츠 응답
 */
data class ExposureContentApiResponse(
    val id: Long,
    val content: ContentInfo,
    val exposure: ExposureInfo,
    val timestamps: Timestamps
) {
    data class ContentInfo(
        val id: Long,
        val url: String,
        val newsletterName: String,
        val language: Language
    )

    data class ExposureInfo(
        val provocativeKeyword: String,
        val provocativeHeadline: String,
        val summaryContent: String,
        val engagementScore: Double? = null
    )

    data class Timestamps(
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    )
}
