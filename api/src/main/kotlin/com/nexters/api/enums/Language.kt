package com.nexters.api.enums

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
