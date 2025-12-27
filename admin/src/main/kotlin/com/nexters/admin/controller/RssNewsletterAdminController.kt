package com.nexters.admin.controller

import com.nexters.external.config.RssFeedProperties
import com.nexters.external.entity.NewsletterSource
import com.nexters.newsletter.service.RssContentService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("prod", "dev")
@RequestMapping("/admin/rss-newsletter")
class RssNewsletterAdminController(
    private val rssContentService: RssContentService,
    private val rssFeedProperties: RssFeedProperties,
) {
    private val logger = LoggerFactory.getLogger(RssNewsletterAdminController::class.java)

    private val rssFeeds: List<String>
        get() = rssFeedProperties.feeds

    @PostMapping("/fetch")
    fun fetchRssFeed(
        @RequestParam feedUrl: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Fetching RSS feed: $feedUrl")

        return try {
            val results = rssContentService.fetchAndSaveRssFeed(feedUrl)
            val savedCount = results[feedUrl] ?: 0

            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "feedUrl" to feedUrl,
                    "newItemsCount" to savedCount,
                    "message" to "Successfully fetched and saved $savedCount new items"
                )
            )
        } catch (e: Exception) {
            logger.error("Error fetching RSS feed: $feedUrl", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "feedUrl" to feedUrl,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    @GetMapping("/recent")
    fun getRecentSources(
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<List<NewsletterSource>> {
        logger.info("Getting recent RSS sources for last $days days")

        val sources = rssContentService.getRecentSources(days)
        return ResponseEntity.ok(sources)
    }

    @GetMapping("/feeds")
    fun getAllFeeds(): ResponseEntity<List<String>> {
        logger.info("Getting all RSS feeds")

        val feeds = rssContentService.getAllFeeds()
        return ResponseEntity.ok(feeds)
    }

    @GetMapping("/stats")
    fun getSourceStats(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting RSS source statistics")

        return try {
            val stats = rssContentService.getSourceStats()
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "stats" to stats
                )
            )
        } catch (e: Exception) {
            logger.error("Error getting RSS source stats", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    @PostMapping("/fetch-all")
    fun fetchAllFeeds(): ResponseEntity<Map<String, Any>> {
        logger.info("Fetching all RSS feeds")

        if (rssFeeds.isEmpty()) {
            return ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to "No RSS feeds configured"
                )
            )
        }

        return try {
            val results = rssContentService.fetchAndSaveRssFeed(*rssFeeds.toTypedArray())

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "results" to results,
                    "totalFeeds" to rssFeeds.size,
                    "message" to "Fetched all configured RSS feeds"
                )
            )
        } catch (e: Exception) {
            logger.error("Error fetching all RSS feeds", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    @GetMapping("/detailed-stats")
    fun getDetailedStats(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting detailed RSS statistics")

        return try {
            val stats = rssContentService.getDetailedStats()
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "stats" to stats
                )
            )
        } catch (e: Exception) {
            logger.error("Error getting detailed RSS stats", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
}
