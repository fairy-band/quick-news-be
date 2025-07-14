package com.nexters.admin.controller

import com.nexters.external.service.KeywordService
import com.nexters.external.service.SummaryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ProcessController(
    private val summaryService: SummaryService,
    private val keywordService: KeywordService
) {
    @PostMapping("/summary")
    fun generateSummary(
        @RequestBody request: ContentRequest
    ): ResponseEntity<SummaryResponse> =
        try {
            val result = summaryService.getSummary(request.content)
            ResponseEntity.ok(
                SummaryResponse(
                    success = true,
                    summary = result.summary,
                    provocativeKeywords = result.provocativeKeywords
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(SummaryResponse(success = false, error = "오류가 발생했습니다: ${e.message}"))
        }

    @PostMapping("/keywords")
    fun extractKeywords(
        @RequestBody request: ContentRequest
    ): ResponseEntity<KeywordResponse> =
        try {
            val result = keywordService.extractKeywords(emptyList(), request.content)
            ResponseEntity.ok(
                KeywordResponse(
                    success = true,
                    matchedKeywords = result.matchedKeywords,
                    suggestedKeywords = result.suggestedKeywords,
                    provocativeKeywords = result.provocativeKeywords
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(KeywordResponse(success = false, error = "오류가 발생했습니다: ${e.message}"))
        }
}

data class ContentRequest(
    val content: String
)

data class SummaryResponse(
    val success: Boolean,
    val summary: String? = null,
    val provocativeKeywords: List<String>? = null,
    val error: String? = null
)

data class KeywordResponse(
    val success: Boolean,
    val matchedKeywords: List<String>? = null,
    val suggestedKeywords: List<String>? = null,
    val provocativeKeywords: List<String>? = null,
    val error: String? = null
)
