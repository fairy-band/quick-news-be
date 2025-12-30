package com.nexters.api.dto

import com.nexters.api.enums.Language
import java.time.LocalDateTime

data class ExposureContentListApiResponse(
    val contents: List<ExposureContentApiResponse>,
    val totalCount: Long,
    val hasMore: Boolean,
    val nextOffset: Long?
)

data class ExposureContentApiResponse(
    val id: Long,
    val contentId: Long,
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String,
    val contentUrl: String,
    val newsletterName: String,
    val language: Language,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
