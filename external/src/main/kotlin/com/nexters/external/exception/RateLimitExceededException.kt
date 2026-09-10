package com.nexters.external.exception

import java.time.LocalDateTime

class RateLimitExceededException(
    message: String,
    val limitType: String,
    val modelName: String,
    val retryAt: LocalDateTime? = null,
) : RuntimeException(message)
