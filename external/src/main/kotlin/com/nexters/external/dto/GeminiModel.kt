package com.nexters.external.dto

enum class GeminiModel(
    val modelName: String
) {
    TWO_ZERO_FLASH_LITE("gemini-2.0-flash-lite"),
    TWO_ZERO_FLASH("gemini-2.0-flash"),
    TWO_FIVE_FLASH("gemini-2.5-flash"),
}
