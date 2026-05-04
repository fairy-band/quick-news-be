package com.nexters.external.service

import com.nexters.external.dto.RssFeed
import com.nexters.external.dto.RssFeedConfig
import com.nexters.external.dto.RssItem
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@Service
@Profile("prod", "dev")
class RssReaderService(
    private val config: RssFeedConfig = RssFeedConfig()
) {
    private val logger = LoggerFactory.getLogger(RssReaderService::class.java)

    fun fetchFeed(feedUrl: String): RssFeed? {
        if (!feedUrl.isValidRssFeedUrl()) {
            logger.warn("Invalid feed URL: $feedUrl")
            return null
        }

        return retryWithBackoff(config.maxRetries) {
            parseFeed(feedUrl)
        }
    }

    private fun parseFeed(feedUrl: String): RssFeed {
        logger.debug("Fetching RSS feed from: $feedUrl")

        val finalUrl = followRedirects(feedUrl)
        logger.debug("Final URL after redirects: $finalUrl")

        val connection =
            URI(finalUrl).toURL().openConnection().apply {
                setRequestProperty("User-Agent", config.userAgent)
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                setRequestProperty("Accept-Encoding", "gzip, deflate, br")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Connection", "keep-alive")
                connectTimeout = config.connectTimeout
                readTimeout = config.readTimeout
            }

        val feed = SyndFeedInput().build(XmlReader(connection.getInputStream()))
        val rssFeed =
            RssFeed(
                title = feed.title ?: "Untitled Feed",
                description = feed.description,
                link = feed.link ?: feedUrl,
                language = feed.language,
                publishedDate = feed.publishedDate?.toLocalDateTime(),
                items = feed.entries.map { entry -> parseRssEntry(entry) }
            )

        logger.info("Successfully parsed RSS feed: ${feed.title} (${feed.entries.size} items)")
        return rssFeed
    }

    private fun parseRssEntry(entry: SyndEntry): RssItem =
        RssItem(
            title = entry.title ?: "Untitled",
            description = entry.description?.value,
            link = entry.link ?: "",
            publishedDate = entry.publishedDate?.toLocalDateTime(),
            author = entry.author,
            categories = entry.categories?.map { it.name } ?: emptyList(),
            content = entry.contents?.firstOrNull()?.value
        )

    private fun <T> retryWithBackoff(
        maxRetries: Int,
        block: () -> T
    ): T? {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                logger.warn("Attempt ${attempt + 1}/$maxRetries failed: ${e.message}")

                if (attempt < maxRetries - 1) {
                    Thread.sleep(config.retryDelayMs * (attempt + 1))
                }
            }
        }

        logger.error("All $maxRetries attempts failed", lastException)
        return null
    }

    private fun followRedirects(
        url: String,
        maxRedirects: Int = 5
    ): String {
        var currentUrl = url
        var redirectCount = 0

        while (redirectCount < maxRedirects) {
            val connection =
                URI(currentUrl).toURL().openConnection() as? HttpURLConnection
                    ?: return currentUrl

            connection.apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", config.userAgent)
                connectTimeout = config.connectTimeout
                readTimeout = config.readTimeout
            }

            val responseCode = connection.responseCode

            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank()) {
                    logger.warn("Redirect response without Location header")
                    return currentUrl
                }

                currentUrl = location
                redirectCount++
                logger.debug("Following redirect to: $currentUrl")
            } else {
                return currentUrl
            }

            connection.disconnect()
        }

        logger.warn("Max redirects ($maxRedirects) reached for URL: $url")
        return currentUrl
    }
}

private fun String.isValidRssFeedUrl(): Boolean = isNotBlank() && (startsWith("http://") || startsWith("https://"))

private fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this.toInstant(), ZoneId.systemDefault())
