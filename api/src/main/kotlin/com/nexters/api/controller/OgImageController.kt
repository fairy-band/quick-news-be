package com.nexters.api.controller

import com.nexters.api.dto.OgImageRequest
import com.nexters.api.service.OgImageService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/og")
class OgImageController(
    private val ogImageService: OgImageService
) {
    @GetMapping("/image")
    fun generateOgImage(
        @RequestParam title: String,
        @RequestParam tag: String,
        @RequestParam newsletterName: String,
        @RequestParam(defaultValue = "#DCFF64") textColor: String
    ): ResponseEntity<ByteArray> =
        try {
            val imageBytes = ogImageService.generateOgImage(title, tag, newsletterName, textColor)

            val headers =
                HttpHeaders().apply {
                    contentType = MediaType.IMAGE_PNG
                    contentLength = imageBytes.size.toLong()
                    set("Cache-Control", "public, max-age=3600") // 1시간 캐시
                }

            ResponseEntity(imageBytes, headers, HttpStatus.OK)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

    @GetMapping("/preview")
    fun previewOgImage(
        @RequestParam(defaultValue = "네온, PostgreSQL전문가도 놓친 치명적 실수") title: String,
        @RequestParam(defaultValue = "Kotlin") tag: String,
        @RequestParam(defaultValue = "안드로이드 위클리") newsletterName: String,
        @RequestParam(defaultValue = "#DCFF64") textColor: String
    ): ResponseEntity<ByteArray> = generateOgImage(title, tag, newsletterName, textColor)

    @PostMapping("/image")
    fun generateOgImagePost(
        @RequestBody request: OgImageRequest
    ): ResponseEntity<ByteArray> = generateOgImage(request.title, request.tag, request.newsletterName, request.textColor)
}
