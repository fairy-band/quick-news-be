package com.nexters.api.service

import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.enums.Language
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.enums.ContentProviderType
import com.nexters.external.repository.UserExposedContentMappingRepository
import com.nexters.external.service.PopularNewsletterSnapshotService
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class NewsletterContentsService(
    private val dayArchiveResolver: DailyContentArchiveResolver,
    private val popularNewsletterSnapshotService: PopularNewsletterSnapshotService,
    private val userExposedContentMappingRepository: UserExposedContentMappingRepository,
) {
    fun getNewsletterContents(
        userId: Long,
        publishedDate: LocalDate = LocalDate.now(),
    ): ContentViewApiResponse {
        val cards =
            dayArchiveResolver.resolveTodayContentArchive(userId, publishedDate).exposureContents.map { exposureContent ->
                exposureContent.toCard()
            }

        val topExposureContents = popularNewsletterSnapshotService.findLatestFeaturedExposureContents(limit = 10)

        val contentIds = topExposureContents.map { it.content.id }.filterNotNull()
        val exposedContentIds =
            if (contentIds.isNotEmpty()) {
                userExposedContentMappingRepository.findExposedContentIdsByUserIdAndContentIds(userId, contentIds)
            } else {
                emptySet()
            }

        val unexposedTrendingCard =
            topExposureContents
                .firstOrNull { it.content.id !in exposedContentIds }
                ?.toCard()

        val trendingCard = unexposedTrendingCard ?: topExposureContents.firstOrNull()?.toCard() ?: cards.firstOrNull()

        val filteredCards =
            trendingCard?.let { trending ->
                cards.filter { it.id != trending.id }
            } ?: cards

        return ContentViewApiResponse(
            publishedDate = publishedDate,
            trendingCard = trendingCard,
            cards = filteredCards,
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
            cardType = this.content.contentProvider?.type ?: ContentProviderType.UNKNOWN
        )

    private fun DailyContentArchive.ExposureContentSnapshot.toCard(): ContentViewApiResponse.ContentCardApiResponse =
        ContentViewApiResponse.ContentCardApiResponse(
            id = id,
            title = provocativeHeadline,
            topKeyword = provocativeKeyword,
            summary = summaryContent,
            contentUrl = content.originalUrl,
            imageUrl = content.imageUrl,
            newsletterName = content.newsletterName,
            language = Language.fromString(content.contentProvider?.language),
            cardType = content.contentProvider?.type ?: ContentProviderType.UNKNOWN
        )
}
