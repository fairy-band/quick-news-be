package com.nexters.api.dto

import java.time.LocalDate

data class ContentViewApiResponse(
    val publishedDate: LocalDate,
    val cards: List<ContentCardApiResponse>,
) {
    data class ContentCardApiResponse(
        val id: Long,
        val title: String,
        val topKeyword: String,
        val summary: String,
        val contentUrl: String,
        val newsletterName: String,
        val language: Language,
    )

    enum class Language {
        ENGLISH,
        KOREAN,
        ;

        companion object {
            fun fromString(language: String?): Language =
                when (language?.lowercase()) {
                    "ko", "korean", "한국어" -> KOREAN
                    else -> ENGLISH
                }
        }
    }
}
