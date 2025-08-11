package com.nexters.newsletterfeeder.config

import com.nexters.external.service.RssNewsletterService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
@Profile("prod")
class RssFeedScheduler(
    private val rssNewsletterService: RssNewsletterService,
    @Value("\${rss.feeds:}") private val rssFeeds: String
) {
    private val logger = LoggerFactory.getLogger(RssFeedScheduler::class.java)

    @Scheduled(fixedDelayString = "\${rss.scheduler.fetch.delay:3600000}")
    fun fetchRssFeeds() {
        if (rssFeeds.isBlank()) {
            logger.debug("No RSS feeds configured, skipping fetch")
            return
        }

        val feedUrls = rssFeeds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        logger.info("Starting scheduled RSS feed fetch for ${feedUrls.size} feeds")

        val results = rssNewsletterService.fetchMultipleFeeds(feedUrls)

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

    @Scheduled(cron = "\${rss.scheduler.stats.cron:0 0 */6 * * *}")
    fun logContentStats() {
        val stats = rssNewsletterService.getContentStats()

        logger.info("=== RSS Content Statistics ===")
        stats.forEach { (feedUrl, stat) ->
            logger.info("Feed: $feedUrl")
            logger.info("  Total: ${stat.totalCount}, Processed: ${stat.processedCount}, Unprocessed: ${stat.unprocessedCount}")
            logger.info("  Last updated: ${stat.lastUpdated}")
        }

        val totalContent = stats.values.sumOf { it.totalCount }
        val totalProcessed = stats.values.sumOf { it.processedCount }
        logger.info("Overall: Total content: $totalContent, Processed: $totalProcessed")
    }
}
