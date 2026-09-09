package com.nexters.api.controller

import com.nexters.newsletter.service.NewsletterRepairPreview
import com.nexters.newsletter.service.NewsletterRepairPreviewService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Internal read-only endpoint consumed by the local curation admin. */
@RestController
@RequestMapping("/api/internal/newsletter-repair")
class NewsletterRepairPreviewController(
    private val newsletterRepairPreviewService: NewsletterRepairPreviewService,
) {
    @GetMapping("/sources/{sourceId}/preview")
    fun preview(
        @PathVariable sourceId: String,
    ): ResponseEntity<NewsletterRepairPreview> = ResponseEntity.ok(newsletterRepairPreviewService.preview(sourceId))
}
