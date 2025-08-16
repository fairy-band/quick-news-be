package com.nexters.api.controller

import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.dto.ContentViewApiResponse.ContentCardApiResponse
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/newsletters")
@Tag(name = "Newsletter API", description = "뉴스레터 관련 API")
class NewsletterApiController(
    private val dayArchiveResolver: DailyContentArchiveResolver,
) {
    @GetMapping("/contents/{userId}")
    fun getNewsletterContents(
        @PathVariable userId: Long,
        publishedDate: LocalDate = LocalDate.now(),
    ): ContentViewApiResponse =
        ContentViewApiResponse(
            publishedDate = publishedDate,
            cards =
                dayArchiveResolver.resolveTodayContentArchive(userId).exposureContents.map {
                    ContentCardApiResponse(
                        title = it.provocativeHeadline,
                        topKeyword = it.provocativeKeyword,
                        summary = it.summaryContent,
                        contentUrl = it.content.originalUrl,
                        newsletterName = it.content.newsletterName,
                    )
                },
        )
}
