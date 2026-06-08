package com.nexters.external.repository

import java.time.LocalDate

data class ExposureContentRecommendationCandidateRow(
    val exposureContentId: Long,
    val contentId: Long,
    val contentProviderId: Long?,
    val contentProviderName: String?,
    val newsletterName: String,
    val publishedAt: LocalDate,
    val title: String,
    val provocativeHeadline: String,
    val summaryContent: String,
)
