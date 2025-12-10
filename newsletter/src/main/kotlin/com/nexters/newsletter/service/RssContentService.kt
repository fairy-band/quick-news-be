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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
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
    private val newsletterProcessingService: NewsletterProcessingService,
    @Value("\${rss.ai.daily-limit:100}")
    private val dailyLimit: Int = 100,
) {
    private val logger = LoggerFactory.getLogger(RssContentService::class.java)

    @Transactional
    fun fetchAndSaveFeeds(feedUrls: List<String>): Map<String, Int> =
        feedUrls.associateWith { feedUrl ->
            try {
                fetchAndSaveRssFeed(feedUrl)
            } catch (e: Exception) {
                logger.error("Error processing feed $feedUrl: ${e.message}", e)
                -1
            }
        }

    fun fetchAndSaveRssFeed(feedUrl: String): Int {
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

    fun fetchAndProcessRssFeed(feedUrl: String): Int {
        logger.info("Fetching and processing RSS feed from: $feedUrl")

        val feedMetadata =
            rssReaderService.fetchFeed(feedUrl) ?: run {
                logger.error("Failed to fetch RSS feed from: $feedUrl")
                return 0
            }

        val processedCount =
            feedMetadata.items.count { item ->
                if (isItemAlreadyProcessed(item.link)) {
                    false
                } else {
                    saveRssItem(feedUrl, feedMetadata.title, item)
                    true
                }
            }

        logger.info("Processed $processedCount new items from feed: $feedUrl")
        return processedCount
    }

    private fun isItemAlreadyProcessed(itemUrl: String): Boolean = rssProcessingStatusRepository.findByItemUrl(itemUrl) != null

    private fun saveRssItem(
        feedUrl: String,
        feedTitle: String,
        item: RssItem,
    ) {
        val newsletterSource = createNewsletterSource(feedUrl, feedTitle, item)
        val savedSource = newsletterSourceService.save(newsletterSource)

        val processingStatus = createProcessingStatus(feedUrl, feedTitle, item, savedSource.id!!.toString())
        val savedStatus = rssProcessingStatusRepository.save(processingStatus)

        tryProcessItemWithAi(savedStatus, item.title)

        logger.debug("Saved new RSS source: ${item.title}")
    }

    private fun createNewsletterSource(
        feedUrl: String,
        feedTitle: String,
        item: RssItem,
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

    private fun tryProcessItemWithAi(
        status: RssProcessingStatus,
        itemTitle: String,
    ) {
        try {
            logger.info("Starting immediate RSS AI processing for: ${status.title}")
            processRssItem(status)
            logger.info("Immediately processed RSS item with AI: $itemTitle")
        } catch (e: Exception) {
            logger.error("Failed to process RSS item immediately: $itemTitle", e)
        }
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

    private fun getRssSources(): List<NewsletterSource> =
        newsletterSourceService
            .findAll()
            .filter { it.senderEmail.startsWith("rss@") }

    @Transactional
    fun processDailyRssWithAi(): ProcessingResult {
        logger.info("Starting RSS AI processing")

        val todayStart = LocalDate.now().atStartOfDay()
        val processedToday = rssProcessingStatusRepository.countProcessedToday(todayStart).toInt()

        if (processedToday >= dailyLimit) {
            logger.info("Daily limit reached: $processedToday/$dailyLimit items processed today")
            return ProcessingResult(0, 0, processedToday, "Daily limit reached")
        }

        val remainingQuota = dailyLimit - processedToday
        logger.info("Processing up to $remainingQuota RSS items (already processed today: $processedToday)")

        val unprocessedItems = fetchUnprocessedItems(remainingQuota)
        val (processedCount, errorCount) = processItems(unprocessedItems, remainingQuota)

        val totalProcessedToday = processedToday + processedCount
        logger.info("RSS AI processing completed. Processed: $processedCount, Errors: $errorCount")

        return ProcessingResult(processedCount, errorCount, totalProcessedToday, "Processing completed")
    }

    fun getProcessingStats(): ProcessingStats {
        val todayStart = LocalDate.now().atStartOfDay()
        val processedToday = rssProcessingStatusRepository.countProcessedToday(todayStart).toInt()
        val totalPending = countPendingItems()

        return ProcessingStats(
            processedToday = processedToday,
            dailyLimit = dailyLimit,
            pending = totalPending,
            remainingQuota = maxOf(0, dailyLimit - processedToday),
        )
    }

    private fun fetchUnprocessedItems(limit: Int): List<RssProcessingStatus> {
        val pageable = PageRequest.of(0, limit)
        return rssProcessingStatusRepository.findUnprocessedByPriority(pageable).content
    }

    private fun processItems(
        items: List<RssProcessingStatus>,
        totalQuota: Int,
    ): Pair<Int, Int> {
        var processedCount = 0
        var errorCount = 0

        items.forEach { status ->
            try {
                processRssItem(status)
                processedCount++
                logger.info("Processed RSS item $processedCount/$totalQuota: ${status.title}")
            } catch (e: Exception) {
                errorCount++
                handleProcessingError(status, e)
            }
        }

        return Pair(processedCount, errorCount)
    }

    private fun processRssItem(status: RssProcessingStatus) {
        val newsletterSource = findNewsletterSourceOrDelete(status) ?: return

        val content = createContentFromSource(newsletterSource)
        val savedContent = contentService.save(content)

        processContentWithAi(savedContent)
        updateProcessingStatus(status, savedContent.id)

        logger.info(
            "Fully processed RSS item: ${savedContent.title} (ID: ${savedContent.id})",
        )
    }

    private fun findNewsletterSourceOrDelete(status: RssProcessingStatus): NewsletterSource? {
        val source = newsletterSourceService.findById(status.newsletterSourceId)
        if (source == null) {
            logger.warn("NewsletterSource not found for ID: ${status.newsletterSourceId}, removing RssProcessingStatus")
            rssProcessingStatusRepository.delete(status)
        }
        return source
    }

    private fun createContentFromSource(newsletterSource: NewsletterSource): Content {
        val contentProvider = resolveContentProvider(newsletterSource.sender)

        return Content(
            newsletterSourceId = newsletterSource.id,
            title = newsletterSource.subject ?: "Untitled",
            content = newsletterSource.content,
            newsletterName = newsletterSource.sender,
            originalUrl = newsletterSource.headers["RSS-Item-URL"] ?: "",
            publishedAt = newsletterSource.receivedDate.toLocalDate(),
            contentProvider = contentProvider,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    private fun resolveContentProvider(newsletterName: String): ContentProvider? =
        try {
            contentProviderService.getByName(newsletterName)
        } catch (e: Exception) {
            logger.warn("Failed to resolve content provider for: $newsletterName", e)
            null
        }

    private fun processContentWithAi(content: Content) {
        try {
            newsletterProcessingService.processExistingContent(content)
            logger.info("AI processing completed for content ID: ${content.id}")
        } catch (e: Exception) {
            logger.error("Failed to process content with AI for content ID: ${content.id}", e)
            throw e
        }
    }

    private fun updateProcessingStatus(
        status: RssProcessingStatus,
        contentId: Long?,
    ) {
        status.aiProcessed = true
        status.aiProcessedAt = LocalDateTime.now()
        status.contentId = contentId
        rssProcessingStatusRepository.save(status)
    }

    private fun handleProcessingError(
        status: RssProcessingStatus,
        error: Exception,
    ) {
        logger.error("Error processing RSS item: ${status.title}", error)
        status.processingError = "AI processing failed: ${error.message}"
        rssProcessingStatusRepository.save(status)
    }

    private fun countPendingItems(): Int =
        rssProcessingStatusRepository
            .findByAiProcessedFalseAndIsProcessedTrue(PageRequest.of(0, 1))
            .totalElements
            .toInt()

    data class SourceStats(
        val totalCount: Int,
        val processedCount: Int,
        val unprocessedCount: Int,
        val lastUpdated: LocalDateTime?,
    )

    data class ProcessingResult(
        val processedCount: Int,
        val errorCount: Int,
        val totalProcessedToday: Int,
        val message: String,
    )

    data class ProcessingStats(
        val processedToday: Int,
        val dailyLimit: Int,
        val pending: Int,
        val remainingQuota: Int,
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
