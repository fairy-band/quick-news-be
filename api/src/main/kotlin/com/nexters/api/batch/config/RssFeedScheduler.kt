package com.nexters.api.batch.config

import com.nexters.external.config.RssFeedProperties
import com.nexters.newsletter.service.RssContentService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI

@Component
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class RssFeedScheduler(
    private val rssContentService: RssContentService,
    private val rssFeedProperties: RssFeedProperties,
) {
    @Value("\${rss.scheduler.fetch.medium-delay-ms:15000}")
    private var mediumFeedDelayMs: Long = 15_000

    private val rssFeeds: List<String>
        get() = rssFeedProperties.feeds

    private val logger = LoggerFactory.getLogger(RssFeedScheduler::class.java)
    private var lastMediumFeedFetchStartedAt: Long = 0

    @Scheduled(fixedDelayString = "\${rss.scheduler.fetch.delay:3600000}")
    fun fetchRssFeeds() {
        if (rssFeeds.isEmpty()) {
            logger.debug("No RSS feeds configured, skipping fetch")
            return
        }

        logger.info("Starting scheduled RSS feed fetch for ${rssFeeds.size} feeds")

        val results = linkedMapOf<String, Int>()
        rssFeeds.forEach { feedUrl ->
            waitForMediumRateLimit(feedUrl)
            results[feedUrl] = rssContentService.fetchAndSaveRssFeed(feedUrl)[feedUrl] ?: -1
        }

        results.forEach { (feedUrl, count) ->
            if (count >= 0) {
                logger.info("Fetched $count new items from: $feedUrl")
            } else {
                logger.error("Failed to fetch feed: $feedUrl")
            }
        }

        val totalNewItems = results.values.filter { it >= 0 }.sum()
        logger.info("Completed RSS feed fetch. Total new items: $totalNewItems")
    }

    private fun waitForMediumRateLimit(feedUrl: String) {
        if (!feedUrl.isMediumFeed()) {
            return
        }

        val now = System.currentTimeMillis()
        val waitMs = (lastMediumFeedFetchStartedAt + mediumFeedDelayMs - now).coerceAtLeast(0)
        if (waitMs > 0) {
            logger.info("Waiting ${waitMs}ms before fetching Medium RSS feed: $feedUrl")
            Thread.sleep(waitMs)
        }
        lastMediumFeedFetchStartedAt = System.currentTimeMillis()
    }

    @Scheduled(cron = "\${rss.scheduler.stats.cron:0 0 */6 * * *}")
    fun logSourceStats() {
        val stats = rssContentService.getSourceStats()

        logger.info("=== RSS Source Statistics ===")
        stats.forEach { (feedName, stat) ->
            logger.info("Feed: $feedName")
            logger.info("  Total: ${stat.totalCount}, Processed: ${stat.processedCount}, Unprocessed: ${stat.unprocessedCount}")
            logger.info("  Last updated: ${stat.lastUpdated}")
        }

        val totalSources = stats.values.sumOf { it.totalCount }
        val totalProcessed = stats.values.sumOf { it.processedCount }
        logger.info("Overall: Total sources: $totalSources, Processed: $totalProcessed")
    }
}

private fun String.isMediumFeed(): Boolean =
    runCatching {
        val host = URI(trim()).host.orEmpty().lowercase()
        host == "medium.com" || host.endsWith(".medium.com")
    }.getOrDefault(false)
