package com.nexters.external.dto

data class RssFeedConfig(
    val connectTimeout: Int = 30000, // 30초로 증가
    val readTimeout: Int = 30000, // 30초로 증가
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 2000, // 2초로 증가
    val userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"
)
