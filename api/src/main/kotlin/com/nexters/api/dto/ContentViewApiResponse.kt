package com.nexters.api.dto

import java.time.LocalDate

data class ContentViewApiResponse(
    val publishedDate: LocalDate,
    val cards: List<ContentCardApiResponse>,
) {
    data class ContentCardApiResponse(
        val title: String,
        val category: String,
        val topKeyword: String,
        val summary: String,
        val contentUrl: String,
        val newsletterName: String,
        val newsletterUrl: String,
    )
}
