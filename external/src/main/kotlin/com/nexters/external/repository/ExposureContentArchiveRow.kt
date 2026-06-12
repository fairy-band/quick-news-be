package com.nexters.external.repository

import com.nexters.external.enums.ContentProviderType
import java.time.LocalDateTime

data class ExposureContentArchiveRow(
    val exposureContentId: Long,
    val contentId: Long,
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String,
    val contentUrl: String,
    val imageUrl: String?,
    val newsletterName: String,
    val contentProviderId: Long?,
    val contentProviderLanguage: String?,
    val contentProviderType: ContentProviderType?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
