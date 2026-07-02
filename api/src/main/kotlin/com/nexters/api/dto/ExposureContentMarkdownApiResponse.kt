package com.nexters.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "노출 컨텐츠 마크다운 응답")
data class ExposureContentMarkdownApiResponse(
    @Schema(description = "노출 컨텐츠 ID", example = "1")
    val exposureContentId: Long,
    @Schema(description = "마크다운 내용", example = "# 요약본\\n\\n이것은 요약된 내용입니다.")
    val markdownContent: String
)
