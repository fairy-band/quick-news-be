package com.nexters.external.exception

class RateLimitExceededException(
    message: String,
    val limitType: String,
    val modelName: String
) : RuntimeException(message)
