package com.nexters.external.service

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId

@Service
@Profile("prod")
class RssReaderService {
    private val logger = LoggerFactory.getLogger(RssReaderService::class.java)

    data class RssFeedItem(
        val title: String,
        val description: String?,
        val link: String,
        val publishedDate: LocalDateTime?,
        val author: String?,
        val categories: List<String>,
        val content: String?
    )

    data class RssFeedMetadata(
        val title: String,
        val description: String?,
        val link: String,
        val language: String?,
        val publishedDate: LocalDateTime?,
        val items: List<RssFeedItem>
    )

    fun readRssFeed(feedUrl: String): RssFeedMetadata? =
        try {
            val url = URL(feedUrl)
            val connection = url.openConnection()
            
            // User-Agent 및 헤더 설정으로 접근 차단 방지
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val input = SyndFeedInput()
            val feed: SyndFeed = input.build(XmlReader(connection.getInputStream()))

            val feedMetadata =
                RssFeedMetadata(
                    title = feed.title ?: "",
                    description = feed.description,
                    link = feed.link ?: feedUrl,
                    language = feed.language,
                    publishedDate =
                        feed.publishedDate?.let {
                            LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                        },
                    items = feed.entries.map { entry -> parseRssEntry(entry) }
                )

            logger.info("Successfully parsed RSS feed: ${feed.title}")
            feedMetadata
        } catch (e: Exception) {
            logger.error("Error reading RSS feed from $feedUrl", e)
            null
        }

    private fun parseRssEntry(entry: SyndEntry): RssFeedItem =
        RssFeedItem(
            title = entry.title ?: "",
            description = entry.description?.value,
            link = entry.link ?: "",
            publishedDate =
                entry.publishedDate?.let {
                    LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault())
                },
            author = entry.author,
            categories = entry.categories?.map { it.name } ?: emptyList(),
            content = entry.contents?.firstOrNull()?.value
        )

    fun readMultipleFeeds(feedUrls: List<String>): List<RssFeedMetadata> =
        feedUrls.mapNotNull { url ->
            readRssFeed(url)
        }
}
