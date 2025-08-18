package com.nexters.api.controller

import com.nexters.external.service.ExposureContentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/share")
@Tag(name = "OG Share API", description = "카카오톡 공유용 OG 페이지 생성 API")
class OgShareController(
    private val exposureContentService: ExposureContentService
) {
    @GetMapping("/og")
    @Operation(summary = "OG 공유 페이지", description = "카카오톡 공유용 페이지를 생성합니다. 노출 컨텐츠 ID로 실제 데이터를 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "페이지 생성 성공"),
            ApiResponse(responseCode = "404", description = "컨텐츠를 찾을 수 없음")
        ]
    )
    fun generateOgSharePage(
        @Parameter(description = "노출 컨텐츠 ID", example = "1")
        @RequestParam exposureContentId: Long,
        @Parameter(description = "배경색", example = "#8B5CF6")
        @RequestParam(defaultValue = "#8B5CF6") backgroundColor: String,
        @Parameter(description = "텍스트색", example = "#FFFFFF")
        @RequestParam(defaultValue = "#FFFFFF") textColor: String,
        model: Model,
        request: HttpServletRequest
    ): String {
        // ExposureContent 조회
        val exposureContent = exposureContentService.getExposureContentById(exposureContentId)

        // 현재 요청 URL 생성 (OG 메타태그용)
        val currentUrl = "${request.scheme}://${request.serverName}:${request.serverPort}${request.requestURI}"
        val shareUrl = "$currentUrl?${request.queryString ?: ""}"

        model.addAttribute("title", exposureContent.provocativeHeadline)
        model.addAttribute("category", exposureContent.provocativeKeyword)
        model.addAttribute("newsletterName", exposureContent.content.newsletterName)
        model.addAttribute("backgroundColor", backgroundColor)
        model.addAttribute("textColor", textColor)
        model.addAttribute("shareUrl", shareUrl)

        return "og-share"
    }

    @GetMapping("/og/example")
    @Operation(summary = "예시 OG 공유 페이지", description = "첫 번째 노출 컨텐츠로 예시 OG 공유 페이지를 생성합니다.")
    fun generateExampleOgSharePage(
        model: Model,
        request: HttpServletRequest
    ): String {
        val currentUrl = "${request.scheme}://${request.serverName}:${request.serverPort}${request.requestURI}"

        // 첫 번째 노출 컨텐츠를 가져오거나 예시 데이터 사용
        val exposureContents = exposureContentService.getAllExposureContents()

        if (exposureContents.isNotEmpty()) {
            val firstContent = exposureContents.first()
            model.addAttribute("title", firstContent.provocativeHeadline)
            model.addAttribute("category", firstContent.provocativeKeyword)
            model.addAttribute("newsletterName", firstContent.content.newsletterName)
        } else {
            // 데이터가 없는 경우 예시 데이터 사용
            model.addAttribute("title", "네온, PostgreSQL, 전문가도 놓친 치명적 실수")
            model.addAttribute("category", "Kotlin")
            model.addAttribute("newsletterName", "안드로이드 위클리")
        }

        model.addAttribute("backgroundColor", "#8B5CF6")
        model.addAttribute("textColor", "#FFFFFF")
        model.addAttribute("shareUrl", currentUrl)

        return "og-share"
    }
}
