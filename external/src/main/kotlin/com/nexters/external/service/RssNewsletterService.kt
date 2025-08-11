package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.repository.ContentRepository
import com.nexters.external.service.ContentService
import com.nexters.external.service.SummaryService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Profile("prod")
class RssNewsletterService(
    private val rssReaderService: RssReaderService,
    private val contentRepository: ContentRepository,
    private val contentService: ContentService,
    private val summaryService: SummaryService
) {
    private val logger = LoggerFactory.getLogger(RssNewsletterService::class.java)

    @Transactional
    fun fetchAndSaveRssFeed(feedUrl: String): Int {
        logger.info("Fetching RSS feed from: $feedUrl")

        val feedMetadata = rssReaderService.readRssFeed(feedUrl)
        if (feedMetadata == null) {
            logger.error("Failed to fetch RSS feed from: $feedUrl")
            return 0
        }

        var savedCount = 0
        feedMetadata.items.forEach { item ->
            val existingContent = contentRepository.findByOriginalUrl(item.link)

            if (existingContent == null) {
                val contentText = buildContentText(item)

                val content =
                    Content(
                        title = item.title,
                        content = contentText,
                        newsletterName = feedMetadata.title,
                        originalUrl = item.link,
                        publishedAt = item.publishedDate?.toLocalDate() ?: LocalDate.now()
                    )

                val savedContent = contentRepository.save(content)
                savedCount++
                logger.debug("Saved new RSS content: ${item.title}")

                // 요약 생성
                summaryService.summarizeAndSave(savedContent)
            } else {
                logger.debug("RSS content already exists: ${item.link}")
            }
        }

        logger.info("Saved $savedCount new items from feed: $feedUrl")
        return savedCount
    }

    private fun buildContentText(item: RssReaderService.RssFeedItem): String =
        buildString {
            item.description?.let { append("$it\n\n") }
            item.content?.let { append("$it\n\n") }
            item.author?.let { append("Author: $it\n") }
            if (item.categories.isNotEmpty()) {
                append("Categories: ${item.categories.joinToString(", ")}\n")
            }
        }.trim()

    @Transactional
    fun fetchAndProcessRssFeed(feedUrl: String): List<Content> {
        val savedCount = fetchAndSaveRssFeed(feedUrl)
        logger.info("Fetched $savedCount new items")

        return getContentByFeedUrl(feedUrl)
    }

    @Transactional
    fun fetchMultipleFeeds(feedUrls: List<String>): Map<String, Int> {
        val results = mutableMapOf<String, Int>()

        feedUrls.forEach { feedUrl ->
            try {
                val count = fetchAndSaveRssFeed(feedUrl)
                results[feedUrl] = count
            } catch (e: Exception) {
                logger.error("Error processing feed $feedUrl: ${e.message}", e)
                results[feedUrl] = -1
            }
        }

        return results
    }

    fun searchContentByKeywords(keywords: List<String>): List<Content> = contentService.findContentsByKeywords(keywords)

    fun getRecentContent(days: Int = 7): List<Content> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())
        return contentRepository.findByPublishedAtBetween(startDate, endDate)
    }

    fun getAllFeeds(): List<String> =
        contentRepository
            .findAll()
            .map { it.newsletterName }
            .distinct()

    fun getContentByFeedUrl(feedUrl: String): List<Content> {
        val feedMetadata = rssReaderService.readRssFeed(feedUrl)
        if (feedMetadata == null) return emptyList()

        val feedTitle = feedMetadata.title
        return contentRepository.findByNewsletterName(feedTitle)
    }

    fun getContentStats(): Map<String, ContentStats> {
        val allContent = contentRepository.findAll()

        return allContent
            .groupBy { it.newsletterName }
            .mapValues { (_, contents) ->
                ContentStats(
                    totalCount = contents.size,
                    processedCount = contents.size, // 모든 content는 저장 시 요약이 생성됨
                    unprocessedCount = 0,
                    lastUpdated = contents.maxOfOrNull { it.createdAt }
                )
            }
    }

    data class ContentStats(
        val totalCount: Int,
        val processedCount: Int,
        val unprocessedCount: Int,
        val lastUpdated: LocalDateTime?
    )
}
