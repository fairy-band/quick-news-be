package com.nexters.api.service

import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.enums.Language
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.enums.ContentProviderType
import com.nexters.external.repository.UserExposedContentMappingRepository
import com.nexters.external.service.PopularNewsletterSnapshotService
import com.nexters.external.service.UserReadContentService
import com.nexters.external.support.MarkdownValidator
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class NewsletterContentsService(
    private val dayArchiveResolver: DailyContentArchiveResolver,
    private val popularNewsletterSnapshotService: PopularNewsletterSnapshotService,
    private val userExposedContentMappingRepository: UserExposedContentMappingRepository,
    private val userReadContentService: UserReadContentService,
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

        val allCardExposureIds = (filteredCards.map { it.id } + listOfNotNull(trendingCard?.id)).distinct()
        val readCardIds = userReadContentService.getReadExposureContentIds(userId, allCardExposureIds)

        val cardsWithReadStatus = filteredCards.map { it.copy(isRead = it.id in readCardIds) }
        val trendingWithReadStatus = trendingCard?.copy(isRead = trendingCard.id in readCardIds)

        return ContentViewApiResponse(
            publishedDate = publishedDate,
            trendingCard = trendingWithReadStatus,
            cards = cardsWithReadStatus,
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
            cardType = this.content.contentProvider?.type ?: ContentProviderType.UNKNOWN,
            estimatedReadingTime = MarkdownValidator.estimateReadingTimeMinutes(this.content.content),
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
            cardType = content.contentProvider?.type ?: ContentProviderType.UNKNOWN,
            estimatedReadingTime = estimatedReadingTime,
        )
}
