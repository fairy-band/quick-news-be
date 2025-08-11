package com.nexters.admin.controller

import com.nexters.external.entity.Content
import com.nexters.external.service.RssNewsletterService
import org.slf4j.LoggerFactory
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
    private val rssNewsletterService: RssNewsletterService
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

    @GetMapping("/search")
    fun searchByKeywords(
        @RequestParam keywords: List<String>
    ): ResponseEntity<List<Content>> {
        logger.info("Searching RSS content by keywords: $keywords")

        val contents = rssNewsletterService.searchContentByKeywords(keywords)
        return ResponseEntity.ok(contents)
    }

    @GetMapping("/recent")
    fun getRecentContent(
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<List<Content>> {
        logger.info("Getting recent RSS content for last $days days")

        val contents = rssNewsletterService.getRecentContent(days)
        return ResponseEntity.ok(contents)
    }

    @GetMapping("/feeds")
    fun getAllFeeds(): ResponseEntity<List<String>> {
        logger.info("Getting all RSS feeds")

        val feeds = rssNewsletterService.getAllFeeds()
        return ResponseEntity.ok(feeds)
    }

    @GetMapping("/stats")
    fun getContentStats(): ResponseEntity<Map<String, RssNewsletterService.ContentStats>> {
        logger.info("Getting RSS content statistics")

        val stats = rssNewsletterService.getContentStats()
        return ResponseEntity.ok(stats)
    }
}
