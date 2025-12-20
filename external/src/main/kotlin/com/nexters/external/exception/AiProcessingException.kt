package com.nexters.external.exception

class AiProcessingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
