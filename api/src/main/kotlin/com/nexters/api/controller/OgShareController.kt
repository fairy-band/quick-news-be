package com.nexters.api.controller

import com.nexters.api.service.OgImageService
import com.nexters.external.service.ExposureContentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/share")
@Tag(name = "OG Image API", description = "카카오톡 공유용 OG 이미지 생성 API")
class OgShareController(
    private val exposureContentService: ExposureContentService,
    private val ogImageService: OgImageService
) {
    @GetMapping("/og")
    @Operation(summary = "OG 이미지 생성", description = "카카오톡 공유용 OG 이미지를 생성합니다. 노출 컨텐츠 ID로 실제 데이터를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "이미지 생성 성공"),
            ApiResponse(responseCode = "404", description = "컨텐츠를 찾을 수 없음")
        ]
    )
    fun generateOgImage(
        @Parameter(description = "노출 컨텐츠 ID", example = "1")
        @RequestParam exposureContentId: Long,
        @Parameter(description = "텍스트색", example = "#DCFF64")
        @RequestParam(defaultValue = "#DCFF64") textColor: String
    ): ResponseEntity<ByteArray> {
        // ExposureContent 조회
        val exposureContent = exposureContentService.getExposureContentById(exposureContentId)

        // OG 이미지 생성
        val imageBytes =
            ogImageService.generateOgImage(
                title = exposureContent.provocativeHeadline,
                tag = exposureContent.provocativeKeyword,
                newsletterName = exposureContent.content.newsletterName,
                textColor = textColor
            )

        // HTTP 헤더 설정
        val headers = HttpHeaders()
        headers.contentType = MediaType.IMAGE_PNG
        headers.contentLength = imageBytes.size.toLong()

        return ResponseEntity
            .ok()
            .headers(headers)
            .body(imageBytes)
    }
}
