package com.nexters.api.dto

import com.nexters.external.enums.ContentProviderType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "콘텐츠 제공자 응답")
data class ContentProviderApiResponse(
    @Schema(description = "콘텐츠 제공자 ID", example = "1")
    val id: Long,
    @Schema(description = "콘텐츠 제공자 이름", example = "Kotlin Weekly")
    val name: String,
    @Schema(description = "채널", example = "Kotlin Weekly")
    val channel: String,
    @Schema(description = "언어", example = "ko")
    val language: String,
    @Schema(description = "타입", example = "NEWSLETTER")
    val type: ContentProviderType?,
)
