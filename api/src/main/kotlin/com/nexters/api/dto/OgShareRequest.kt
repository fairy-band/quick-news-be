package com.nexters.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "OG 공유 페이지 생성 요청")
data class OgShareRequest(
    @Schema(description = "노출 컨텐츠 ID", example = "1")
    val exposureContentId: Long,
    @Schema(description = "배경 색상 (헥스 코드)", example = "#8B5CF6")
    val backgroundColor: String = "#8B5CF6",
    @Schema(description = "텍스트 색상 (헥스 코드)", example = "#FFFFFF")
    val textColor: String = "#FFFFFF"
)
