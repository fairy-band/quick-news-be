package com.nexters.external.service

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.entity.RssProcessingStatus
import com.nexters.external.repository.NewsletterSourceRepository
import com.nexters.external.repository.RssProcessingStatusRepository
import com.nexters.external.service.RssAiProcessingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Profile("prod", "dev")
class RssNewsletterService(
    private val rssReaderService: RssReaderService,
    private val newsletterSourceRepository: NewsletterSourceRepository,
    private val rssProcessingStatusRepository: RssProcessingStatusRepository,
    private val rssAiProcessingService: RssAiProcessingService
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
            // 처리 상태 테이블에서 중복 체크
            val existingStatus = rssProcessingStatusRepository.findByItemUrl(item.link)

            if (existingStatus == null) {
                val contentText = buildContentText(item)

                val newsletterSource =
                    NewsletterSource(
                        subject = item.title,
                        sender = feedMetadata.title, // RSS 피드 제목을 sender로 사용
                        senderEmail = "rss@${extractDomainFromUrl(item.link)}", // URL에서 도메인 추출하여 이메일 형태로
                        recipient = "system",
                        recipientEmail = "system@newsletter.ai",
                        content = contentText,
                        contentType = "text/html", // RSS는 HTML 형태
                        receivedDate = item.publishedDate ?: LocalDateTime.now(),
                        headers =
                            mapOf(
                                "RSS-Feed-URL" to feedUrl,
                                "RSS-Item-URL" to item.link,
                                "RSS-Categories" to item.categories.joinToString(",")
                            )
                    )

                val savedSource = newsletterSourceRepository.save(newsletterSource)

                // 처리 상태 저장 (우선순위 계산) - 즉시 AI 처리
                val priority = calculatePriority(feedMetadata.title)
                val processingStatus =
                    RssProcessingStatus(
                        newsletterSourceId = savedSource.id!!,
                        rssUrl = feedUrl,
                        itemUrl = item.link,
                        title = item.title,
                        isProcessed = true,
                        aiProcessed = false, // 아직 AI 처리 안됨
                        priority = priority
                    )
                val savedStatus = rssProcessingStatusRepository.save(processingStatus)

                // 즉시 AI 처리 (Content, Summary, 키워드 생성)
                try {
                    rssAiProcessingService.processRssItemImmediately(savedStatus)
                    logger.info("Immediately processed RSS item with AI: ${item.title}")
                } catch (e: Exception) {
                    logger.error("Failed to process RSS item immediately: ${item.title}", e)
                    // AI 처리 실패해도 RSS 수집은 성공으로 처리
                }

                savedCount++
                logger.debug("Saved new RSS source: ${item.title}")
            } else {
                logger.debug("RSS item already exists: ${item.link}")
            }
        }

        logger.info("Saved $savedCount new items from feed: $feedUrl")
        return savedCount
    }

    private fun buildContentText(item: RssReaderService.RssFeedItem): String =
        buildString {
            item.description?.let {
                val cleanDescription = cleanHtmlContent(it)
                if (cleanDescription.isNotBlank()) {
                    append("$cleanDescription\n\n")
                }
            }
            item.content?.let {
                val cleanContent = cleanHtmlContent(it)
                if (cleanContent.isNotBlank()) {
                    append("$cleanContent\n\n")
                }
            }
            item.author?.let { append("Author: $it\n") }
            if (item.categories.isNotEmpty()) {
                append("Categories: ${item.categories.joinToString(", ")}\n")
            }
        }.trim()

    private fun cleanHtmlContent(htmlContent: String): String =
        htmlContent
            // HTML 태그 제거
            .replace(Regex("<[^>]+>"), " ")
            // HTML 엔티티 디코딩
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            // 연속된 공백을 하나로
            .replace(Regex("\\s+"), " ")
            // 연속된 줄바꿈을 두 개로 제한
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private fun extractDomainFromUrl(url: String): String =
        try {
            val domain = url.substringAfter("://").substringBefore("/").substringBefore(":")
            domain.removePrefix("www.")
        } catch (e: Exception) {
            "unknown.com"
        }

    private fun calculatePriority(feedTitle: String): Int =
        when {
            feedTitle.contains("카카오") -> 100
            feedTitle.contains("우아한") -> 90
            feedTitle.contains("당근") -> 85
            feedTitle.contains("라인") || feedTitle.contains("LINE") -> 80
            feedTitle.contains("JetBrains") -> 75
            feedTitle.contains("Microsoft") || feedTitle.contains("TypeScript") -> 70
            feedTitle.contains("무신사") || feedTitle.contains("29CM") || feedTitle.contains("원티드") -> 65
            else -> 50
        }

    @Transactional
    fun fetchAndProcessRssFeed(feedUrl: String): List<NewsletterSource> {
        val savedCount = fetchAndSaveRssFeed(feedUrl)
        logger.info("Fetched $savedCount new items")

        return getSourcesByFeedUrl(feedUrl)
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

    fun getRecentSources(days: Int = 7): List<NewsletterSource> {
        val endDate = LocalDateTime.now()
        val startDate = endDate.minusDays(days.toLong())
        return newsletterSourceRepository.findByReceivedDateBetween(startDate, endDate)
    }

    fun getAllFeeds(): List<String> =
        newsletterSourceRepository
            .findAll()
            .filter { it.senderEmail.startsWith("rss@") } // RSS 소스만 필터링
            .map { it.sender }
            .distinct()

    fun getSourcesByFeedUrl(feedUrl: String): List<NewsletterSource> {
        val feedMetadata = rssReaderService.readRssFeed(feedUrl) ?: return emptyList()

        val feedTitle = feedMetadata.title
        return newsletterSourceRepository.findBySender(feedTitle)
    }

    fun getSourceStats(): Map<String, SourceStats> {
        val allSources =
            newsletterSourceRepository
                .findAll()
                .filter { it.senderEmail.startsWith("rss@") } // RSS 소스만

        return allSources
            .groupBy { it.sender }
            .mapValues { (_, sources) ->
                SourceStats(
                    totalCount = sources.size,
                    processedCount = sources.size, // RSS 소스는 원문만 저장 (기존 뉴스레터 소스와 동일)
                    unprocessedCount = 0,
                    lastUpdated = sources.mapNotNull { it.createdAt }.maxOrNull()
                )
            }
    }

    data class SourceStats(
        val totalCount: Int,
        val processedCount: Int,
        val unprocessedCount: Int,
        val lastUpdated: LocalDateTime?
    )
}
