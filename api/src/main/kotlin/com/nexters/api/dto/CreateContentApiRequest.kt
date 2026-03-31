package com.nexters.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "콘텐츠 생성 요청")
data class CreateContentApiRequest(
    @Schema(description = "콘텐츠 제목", example = "Kotlin 최신 기능 소개")
    val title: String,
    @Schema(description = "콘텐츠 본문", example = "Kotlin 1.9에서 새로운 기능들이 추가되었습니다...")
    val content: String,
    @Schema(description = "콘텐츠 제공자 이름", example = "Kotlin Weekly")
    val contentProviderName: String,
    @Schema(description = "원본 URL", example = "https://example.com/article")
    val originalUrl: String,
    @Schema(description = "발행 날짜", example = "2024-03-21")
    val publishedAt: LocalDate,
)
