package com.nexters.api.batch.controller

import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillRequest
import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillResponse
import com.nexters.api.batch.service.NewsletterParseOnlyBackfillService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/batch/newsletter-backfill")
@ConditionalOnProperty(
    name = ["newsletter.backfill.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class NewsletterParseOnlyBackfillController(
    private val newsletterParseOnlyBackfillService: NewsletterParseOnlyBackfillService,
) {
    @PostMapping("/parse-only")
    fun createContents(
        @RequestBody(required = false) request: NewsletterParseOnlyBackfillRequest?,
    ): ResponseEntity<NewsletterParseOnlyBackfillResponse> =
        ResponseEntity.ok(
            newsletterParseOnlyBackfillService.createContents(
                request ?: NewsletterParseOnlyBackfillRequest(),
            ),
        )
}
