package com.nexters.external.dto

data class RssFeedConfig(
    val connectTimeout: Int = 15000,
    val readTimeout: Int = 15000,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000,
    val userAgent: String = "Mozilla/5.0 (compatible; RSS Reader)"
)
