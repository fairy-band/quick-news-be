package com.nexters.newsletterfeeder.config

import com.nexters.newsletter.service.RssContentService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("prod", "dev")
class RssFeedScheduler(
    private val rssContentService: RssContentService,
    @Value("\${rss.feeds:}") private val rssFeeds: List<String>
) {
    private val logger = LoggerFactory.getLogger(RssFeedScheduler::class.java)

    @Scheduled(fixedDelayString = "\${rss.scheduler.fetch.delay:3600000}")
    fun fetchRssFeeds() {
        if (rssFeeds.isEmpty()) {
            logger.debug("No RSS feeds configured, skipping fetch")
            return
        }

        logger.info("Starting scheduled RSS feed fetch for ${rssFeeds.size} feeds")

        val results = rssContentService.fetchAndSaveFeeds(rssFeeds)

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
