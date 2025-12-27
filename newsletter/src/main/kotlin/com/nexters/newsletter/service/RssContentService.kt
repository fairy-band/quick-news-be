package com.nexters.newsletter.service

import com.nexters.external.dto.RssItem
import com.nexters.external.dto.toContentText
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.entity.RssProcessingStatus
import com.nexters.external.repository.RssProcessingStatusRepository
import com.nexters.external.service.ContentProviderService
import com.nexters.external.service.ContentService
import com.nexters.external.service.NewsletterSourceService
import com.nexters.external.service.RssReaderService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Profile("prod", "dev")
class RssContentService(
    private val rssReaderService: RssReaderService,
    private val newsletterSourceService: NewsletterSourceService,
    private val contentService: ContentService,
    private val contentProviderService: ContentProviderService,
    private val rssProcessingStatusRepository: RssProcessingStatusRepository,
) {
    private val logger = LoggerFactory.getLogger(RssContentService::class.java)

    @Transactional
    fun fetchAndSaveRssFeed(vararg feedUrls: String): Map<String, Int> =
        feedUrls.associateWith { feedUrl ->
            try {
                processSingleFeed(feedUrl)
            } catch (e: Exception) {
                logger.error("Error processing feed $feedUrl: ${e.message}", e)
                -1
            }
        }

    private fun processSingleFeed(feedUrl: String): Int {
        logger.info("Fetching RSS feed from: $feedUrl")

        val feedMetadata =
            rssReaderService.fetchFeed(feedUrl) ?: run {
                logger.error("Failed to fetch RSS feed from: $feedUrl")
                return 0
            }

        val savedCount =
            feedMetadata.items.count { item ->
                if (isItemAlreadyProcessed(item.link)) {
                    logger.debug("RSS item already exists: ${item.link}")
                    false
                } else {
                    saveRssItem(feedUrl, feedMetadata.title, item)
                    true
                }
            }

        logger.info("Saved $savedCount new items from feed: $feedUrl")
        return savedCount
    }

    fun getRecentSources(days: Int): List<NewsletterSource> {
        val cutoffDate = LocalDateTime.now().minusDays(days.toLong())
        return getRssSources()
            .filter { it.createdAt?.isAfter(cutoffDate) == true }
            .sortedByDescending { it.createdAt }
    }

    fun getAllFeeds(): List<String> =
        getRssSources()
            .mapNotNull { it.headers["RSS-Feed-URL"] }
            .distinct()

    fun getSourceStats(): Map<String, SourceStats> =
        getRssSources()
            .groupBy { it.sender }
            .mapValues { (_, sources) ->
                SourceStats(
                    totalCount = sources.size,
                    processedCount = sources.size,
                    unprocessedCount = 0,
                    lastUpdated = sources.mapNotNull { it.createdAt }.maxOrNull(),
                )
            }

    fun getDetailedStats(): Map<String, FeedDetailedStats> {
        val allSources = getRssSources()
        val allStatuses = rssProcessingStatusRepository.findAll()

        return allSources
            .groupBy { it.sender }
            .mapValues { (feedName, sources) ->
                val feedUrl = sources.firstOrNull()?.headers?.get("RSS-Feed-URL") ?: ""
                val statuses = allStatuses.filter { it.rssUrl == feedUrl }

                FeedDetailedStats(
                    feedName = feedName,
                    feedUrl = feedUrl,
                    totalItems = statuses.size,
                    aiProcessed = statuses.count { it.aiProcessed },
                    pending = statuses.count { !it.aiProcessed && it.isProcessed },
                    errors = statuses.count { it.processingError != null },
                    todayItems = statuses.count { it.createdAt.toLocalDate() == LocalDate.now() },
                    lastFetch = sources.mapNotNull { it.createdAt }.maxOrNull(),
                    priority =
                        statuses
                            .map { it.priority }
                            .average()
                            .takeIf { !it.isNaN() }
                            ?.toInt() ?: 0,
                    successRate =
                        if (statuses.isNotEmpty()) {
                            (statuses.count { it.aiProcessed }.toDouble() / statuses.size * 100).toInt()
                        } else {
                            0
                        }
                )
            }
    }

    private fun isItemAlreadyProcessed(itemUrl: String): Boolean = rssProcessingStatusRepository.findByItemUrl(itemUrl) != null

    private fun saveRssItem(
        feedUrl: String,
        feedTitle: String,
        item: RssItem
    ) {
        val newsletterSource = createNewsletterSource(feedUrl, feedTitle, item)
        val savedSource = newsletterSourceService.save(newsletterSource)

        val content = createContentFromNewsletterSource(savedSource, item)
        contentService.save(content)

        val processingStatus = createProcessingStatus(feedUrl, feedTitle, item, savedSource.id!!)
        rssProcessingStatusRepository.save(processingStatus)

        logger.debug("Saved RSS item: ${item.title}")
    }

    private fun createNewsletterSource(
        feedUrl: String,
        feedTitle: String,
        item: RssItem
    ): NewsletterSource =
        NewsletterSource(
            subject = item.title,
            sender = feedTitle,
            senderEmail = "rss@${item.link.extractDomain()}",
            recipient = "system",
            recipientEmail = "system@newsletter.ai",
            content = item.toContentText(),
            contentType = "text/html",
            receivedDate = item.publishedDate ?: LocalDateTime.now(),
            headers =
                mapOf(
                    "RSS-Feed-URL" to feedUrl,
                    "RSS-Item-URL" to item.link,
                    "RSS-Categories" to item.categories.joinToString(","),
                ),
        )

    private fun createContentFromNewsletterSource(
        newsletterSource: NewsletterSource,
        item: RssItem
    ): Content {
        val contentProvider = resolveContentProvider(newsletterSource.sender)

        return Content(
            newsletterSourceId = newsletterSource.id,
            title = newsletterSource.subject ?: "Untitled",
            content = newsletterSource.content,
            newsletterName = newsletterSource.sender,
            originalUrl = item.link,
            publishedAt = newsletterSource.receivedDate.toLocalDate(),
            contentProvider = contentProvider,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    private fun createProcessingStatus(
        feedUrl: String,
        feedTitle: String,
        item: RssItem,
        newsletterSourceId: String,
    ): RssProcessingStatus =
        RssProcessingStatus(
            newsletterSourceId = newsletterSourceId,
            rssUrl = feedUrl,
            itemUrl = item.link,
            title = item.title,
            isProcessed = true,
            aiProcessed = false,
            priority = feedTitle.calculateFeedPriority(),
        )

    private fun getRssSources(): List<NewsletterSource> = newsletterSourceService.findAll().filter { it.senderEmail.startsWith("rss@") }

    private fun resolveContentProvider(newsletterName: String): ContentProvider? =
        try {
            contentProviderService.findByName(newsletterName)
        } catch (e: Exception) {
            logger.warn("Failed to resolve content provider for: $newsletterName", e)
            null
        }

    data class SourceStats(
        val totalCount: Int,
        val processedCount: Int,
        val unprocessedCount: Int,
        val lastUpdated: LocalDateTime?,
    )

    data class FeedDetailedStats(
        val feedName: String,
        val feedUrl: String,
        val totalItems: Int,
        val aiProcessed: Int,
        val pending: Int,
        val errors: Int,
        val todayItems: Int,
        val lastFetch: LocalDateTime?,
        val priority: Int,
        val successRate: Int
    )
}

private fun String.extractDomain(): String =
    runCatching {
        substringAfter("://")
            .substringBefore("/")
            .substringBefore(":")
            .removePrefix("www.")
    }.getOrDefault("unknown.com")

private fun String.calculateFeedPriority(): Int =
    when {
        contains("카카오") -> 100
        contains("우아한") -> 90
        contains("당근") -> 85
        contains("라인") || contains("LINE") -> 80
        contains("JetBrains") -> 75
        contains("Microsoft") || contains("TypeScript") -> 70
        contains("무신사") || contains("29CM") || contains("원티드") -> 65
        else -> 50
    }
