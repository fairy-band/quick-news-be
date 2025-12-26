package com.nexters.external.dto

enum class GeminiModel(
    val modelName: String,
    val rpm: Int, // Requests Per Minute
    val rpd: Int, // Requests Per Day
) {
    TWO_FIVE_FLASH("gemini-2.5-flash", rpm = 5, rpd = 20),
    TWO_FIVE_FLASH_LITE("gemini-2.5-flash-lite", rpm = 10, rpd = 20),
}
