package com.nexters.external.repository

import java.time.LocalDateTime

data class ExploreContentRow(
    val id: Long,
    val contentId: Long,
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String,
    val contentUrl: String,
    val imageUrl: String?,
    val newsletterName: String,
    val language: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
