package com.nexters.api.service

import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.enums.Language
import com.nexters.external.entity.ExposureContent
import com.nexters.external.service.PopularNewsletterSnapshotService
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class NewsletterContentsService(
    private val dayArchiveResolver: DailyContentArchiveResolver,
    private val popularNewsletterSnapshotService: PopularNewsletterSnapshotService,
) {
    fun getNewsletterContents(
        userId: Long,
        publishedDate: LocalDate = LocalDate.now(),
    ): ContentViewApiResponse {
        val cards =
            dayArchiveResolver.resolveTodayContentArchive(userId, publishedDate).exposureContents.map { exposureContent ->
                exposureContent.toCard()
            }

        val trendingCard =
            popularNewsletterSnapshotService
                .findLatestFeaturedExposureContent()
                ?.toCard()
                ?: cards.firstOrNull()

        return ContentViewApiResponse(
            publishedDate = publishedDate,
            trendingCard = trendingCard,
            cards = cards,
        )
    }

    private fun ExposureContent.toCard(): ContentViewApiResponse.ContentCardApiResponse =
        ContentViewApiResponse.ContentCardApiResponse(
            id = this.id!!,
            title = this.provocativeHeadline,
            topKeyword = this.provocativeKeyword,
            summary = this.summaryContent,
            contentUrl = this.content.originalUrl,
            imageUrl = this.content.imageUrl,
            newsletterName = this.content.newsletterName,
            language = Language.fromString(this.content.contentProvider?.language),
        )
}
