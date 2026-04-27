package com.nexters.api.service

import java.time.LocalDateTime

data class ExploreContentsResult(
    val contents: List<ExploreContentResult>,
    val totalCount: Long,
    val hasMore: Boolean,
    val nextOffset: Long?,
)

data class ExploreContentPageResult(
    val contents: List<ExploreContentResult>,
    val hasMore: Boolean,
    val nextOffset: Long?,
)

data class ExploreContentResult(
    val id: Long,
    val contentId: Long,
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String,
    val contentUrl: String,
    val imageUrl: String? = null,
    val newsletterName: String,
    val language: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
