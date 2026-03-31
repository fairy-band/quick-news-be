package com.nexters.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "콘텐츠 생성 응답")
data class CreateContentApiResponse(
    @Schema(description = "생성된 콘텐츠 ID")
    val id: Long,
    @Schema(description = "콘텐츠 제목")
    val title: String,
    @Schema(description = "뉴스레터 이름")
    val newsletterName: String,
    @Schema(description = "원본 URL")
    val originalUrl: String,
    @Schema(description = "생성 시간")
    val createdAt: LocalDateTime,
)
