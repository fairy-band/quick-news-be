package com.nexters.admin.controller

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.service.RssAiProcessingService
import com.nexters.external.service.RssNewsletterService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("prod")
@RequestMapping("/admin/rss-newsletter")
class RssNewsletterAdminController(
    private val rssNewsletterService: RssNewsletterService,
    private val rssAiProcessingService: RssAiProcessingService,
    @Value("\${rss.feeds:}") private val rssFeeds: String
) {
    private val logger = LoggerFactory.getLogger(RssNewsletterAdminController::class.java)

    @PostMapping("/fetch")
    fun fetchRssFeed(
        @RequestParam feedUrl: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Fetching RSS feed: $feedUrl")

        return try {
            val savedCount = rssNewsletterService.fetchAndSaveRssFeed(feedUrl)
            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "feedUrl" to feedUrl,
                    "newItemsCount" to savedCount,
                    "message" to "Successfully fetched $savedCount new items"
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

    @PostMapping("/fetch-and-process")
    fun fetchAndProcessRssFeed(
        @RequestParam feedUrl: String
    ): ResponseEntity<Map<String, Any>> {
        logger.info("Fetching and processing RSS feed: $feedUrl")

        return try {
            val processedContents = rssNewsletterService.fetchAndProcessRssFeed(feedUrl)
            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "feedUrl" to feedUrl,
                    "processedCount" to processedContents.size,
                    "message" to "Successfully fetched and processed ${processedContents.size} items"
                )
            )
        } catch (e: Exception) {
            logger.error("Error fetching and processing RSS feed: $feedUrl", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "feedUrl" to feedUrl,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    // RSS 컨텐츠는 저장시 즉시 요약이 생성되므로 별도 처리 불필요

    @GetMapping("/recent")
    fun getRecentSources(
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<List<NewsletterSource>> {
        logger.info("Getting recent RSS sources for last $days days")

        val sources = rssNewsletterService.getRecentSources(days)
        return ResponseEntity.ok(sources)
    }

    @GetMapping("/feeds")
    fun getAllFeeds(): ResponseEntity<List<String>> {
        logger.info("Getting all RSS feeds")

        val feeds = rssNewsletterService.getAllFeeds()
        return ResponseEntity.ok(feeds)
    }

    @GetMapping("/stats")
    fun getSourceStats(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting RSS source statistics")

        return try {
            val stats = rssNewsletterService.getSourceStats()
            ResponseEntity.ok(
                mapOf<String, Any>(
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

        if (rssFeeds.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to "No RSS feeds configured"
                )
            )
        }

        return try {
            val feedUrls = rssFeeds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val results = rssNewsletterService.fetchMultipleFeeds(feedUrls)

            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "results" to results,
                    "totalFeeds" to feedUrls.size,
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

    @PostMapping("/process-ai")
    fun processRssWithAi(): ResponseEntity<Map<String, Any>> {
        logger.info("Starting manual RSS AI processing")

        return try {
            val result = rssAiProcessingService.processDailyRssWithAi()
            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "message" to result.message,
                    "processedCount" to result.processedCount,
                    "errorCount" to result.errorCount,
                    "totalProcessedToday" to result.totalProcessedToday
                )
            )
        } catch (e: Exception) {
            logger.error("Error processing RSS with AI", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    @GetMapping("/processing-stats")
    fun getProcessingStats(): ResponseEntity<Map<String, Any>> {
        logger.info("Getting RSS AI processing statistics")

        return try {
            val stats = rssAiProcessingService.getProcessingStats()
            ResponseEntity.ok(
                mapOf<String, Any>(
                    "success" to true,
                    "processedToday" to stats.processedToday,
                    "dailyLimit" to stats.dailyLimit,
                    "pending" to stats.pending,
                    "remainingQuota" to stats.remainingQuota
                )
            )
        } catch (e: Exception) {
            logger.error("Error getting RSS AI processing stats", e)
            ResponseEntity.badRequest().body(
                mapOf<String, Any>(
                    "success" to false,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
}
