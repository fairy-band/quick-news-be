package com.nexters.external.repository

import java.time.LocalDate

data class ContentLookupRow(
    val id: Long,
    val originalUrl: String,
    val newsletterSourceId: String?,
    val publishedAt: LocalDate?,
)

data class ExposureContentLookupRow(
    val id: Long,
    val contentId: Long,
)
