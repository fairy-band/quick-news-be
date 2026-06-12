package com.nexters.external.service

import com.nexters.external.dto.RssFeed
import com.nexters.external.dto.RssFeedConfig
import com.nexters.external.dto.RssItem
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jdom2.Element
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@Service
@Profile("prod", "dev")
class RssReaderService(
    private val representativeImageUrlExtractorService: RepresentativeImageUrlExtractorService,
    private val config: RssFeedConfig = RssFeedConfig()
) {
    private val logger = LoggerFactory.getLogger(RssReaderService::class.java)

    fun fetchFeed(feedUrl: String): RssFeed? {
        if (!feedUrl.isValidRssFeedUrl()) {
            logger.warn("Invalid feed URL: $feedUrl")
            return null
        }

        return retryWithBackoff(feedUrl, config.maxRetries) {
            parseFeed(feedUrl)
        }
    }

    private fun parseFeed(feedUrl: String): RssFeed {
        logger.debug("Fetching RSS feed from: $feedUrl")

        val connection =
            URI(feedUrl).toURL().openConnection().apply {
                if (this is HttpURLConnection) {
                    instanceFollowRedirects = true
                }
                setRequestProperty("User-Agent", config.userAgent)
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Connection", "keep-alive")
                connectTimeout = config.connectTimeout
                readTimeout = config.readTimeout
            }
        connection.validateSuccessfulResponse(feedUrl)

        val feed = SyndFeedInput().build(StringReader(connection.readSanitizedXml(feedUrl)))
        val itemBaseUrl = feed.link?.takeIf { link -> link.isHttpUrl() } ?: feedUrl
        val rssFeed =
            RssFeed(
                title = feed.title ?: "Untitled Feed",
                description = feed.description,
                link = feed.link ?: feedUrl,
                language = feed.language,
                publishedDate = feed.publishedDate?.toLocalDateTime(),
                items = feed.entries.map { entry -> parseRssEntry(entry, itemBaseUrl) }
            )

        logger.info("Successfully parsed RSS feed: ${feed.title} (${feed.entries.size} items)")
        return rssFeed
    }

    private fun parseRssEntry(
        entry: SyndEntry,
        baseUrl: String,
    ): RssItem =
        RssItem(
            title = entry.title?.trim() ?: "Untitled",
            description = entry.description?.value,
            link = entry.link.toAbsoluteHttpUrl(baseUrl) ?: entry.link.orEmpty().trim(),
            publishedDate = entry.publishedDate?.toLocalDateTime(),
            author = entry.author,
            categories = entry.categories?.map { it.name } ?: emptyList(),
            content = entry.extractContent(),
            imageUrl = entry.extractImageUrl(baseUrl),
        )

    private fun SyndEntry.extractContent(): String? {
        val contentCandidates =
            buildList {
                contents
                    ?.mapNotNull { content -> content.value?.trim() }
                    ?.filter { content -> content.isNotBlank() }
                    ?.let { addAll(it) }

                foreignMarkup
                    ?.asSequence()
                    ?.flatMap { element -> element.flatten() }
                    ?.filter { element -> element.isContentEncodedElement() }
                    ?.map { element -> element.value.trim() }
                    ?.filter { content -> content.isNotBlank() }
                    ?.toList()
                    ?.let { addAll(it) }
            }

        return contentCandidates.maxByOrNull { it.length }
    }

    private fun SyndEntry.extractImageUrl(baseUrl: String): String? =
        extractMediaImageUrl()
            ?: extractEnclosureImageUrl()
            ?: representativeImageUrlExtractorService.extractFromHtml(description?.value, link.toAbsoluteHttpUrl(baseUrl) ?: baseUrl)
            ?: representativeImageUrlExtractorService.extractFromHtml(extractContent(), link.toAbsoluteHttpUrl(baseUrl) ?: baseUrl)

    private fun SyndEntry.extractMediaImageUrl(): String? =
        foreignMarkup
            ?.asSequence()
            ?.flatMap { element -> element.flatten() }
            ?.filter { element -> element.isMediaImageElement() }
            ?.mapNotNull { element -> element.getAttributeValue("url").toAbsoluteHttpUrl(link.orEmpty()) }
            ?.firstOrNull()

    private fun Element.flatten(): Sequence<Element> =
        sequence {
            yield(this@flatten)
            children.forEach { child -> yieldAll(child.flatten()) }
        }

    private fun Element.isMediaImageElement(): Boolean {
        val mediaNamespace =
            namespacePrefix.equals("media", ignoreCase = true) ||
                namespaceURI.contains("search.yahoo.com/mrss", ignoreCase = true)
        val imageElement = name.equals("thumbnail", ignoreCase = true) || name.equals("content", ignoreCase = true)
        val imageType = getAttributeValue("type")?.startsWith("image/", ignoreCase = true) ?: true

        return mediaNamespace && imageElement && imageType
    }

    private fun Element.isContentEncodedElement(): Boolean {
        val contentNamespace =
            namespacePrefix.equals("content", ignoreCase = true) ||
                namespaceURI.contains("purl.org/rss/1.0/modules/content", ignoreCase = true)

        return contentNamespace && name.equals("encoded", ignoreCase = true)
    }

    private fun SyndEntry.extractEnclosureImageUrl(): String? =
        enclosures
            ?.firstOrNull { enclosure -> enclosure.type?.startsWith("image/", ignoreCase = true) == true }
            ?.url
            .toAbsoluteHttpUrl(link.orEmpty())

    private fun java.net.URLConnection.validateSuccessfulResponse(url: String) {
        val httpConnection = this as? HttpURLConnection ?: return
        val responseCode = httpConnection.responseCode
        if (responseCode !in 200..299) {
            throw RssFeedHttpException(
                url = url,
                statusCode = responseCode,
                retryAfter = httpConnection.getHeaderField("Retry-After"),
            )
        }
    }

    private fun java.net.URLConnection.readSanitizedXml(feedUrl: String): String {
        val xml =
            getInputStream().use { inputStream ->
                XmlReader(inputStream).use { reader -> reader.readText() }
            }

        return xml.sanitizeInvalidXmlCharacters(feedUrl)
    }

    private fun String.sanitizeInvalidXmlCharacters(feedUrl: String): String {
        var removedCount = 0
        val sanitized = StringBuilder(length)

        codePoints().forEach { codePoint ->
            if (codePoint.isValidXml10CodePoint()) {
                sanitized.appendCodePoint(codePoint)
            } else {
                removedCount++
            }
        }

        if (removedCount == 0) {
            return this
        }

        logger.warn("Removed $removedCount invalid XML character(s) from RSS feed: $feedUrl")
        return sanitized.toString()
    }

    private fun <T> retryWithBackoff(
        feedUrl: String,
        maxRetries: Int,
        block: () -> T
    ): T? {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: RssFeedHttpException) {
                lastException = e
                if (!e.isRetryable) {
                    logger.warn(
                        "Non-retryable RSS HTTP error: status=${e.statusCode}, url=${e.url}, retryAfter=${e.retryAfter}",
                    )
                    return null
                }

                logger.warn("Attempt ${attempt + 1}/$maxRetries failed for RSS feed $feedUrl: ${e.message}")

                if (attempt < maxRetries - 1) {
                    Thread.sleep(config.retryDelayMs * (attempt + 1))
                }
            } catch (e: Exception) {
                lastException = e
                logger.warn("Attempt ${attempt + 1}/$maxRetries failed for RSS feed $feedUrl: ${e.message}")

                if (attempt < maxRetries - 1) {
                    Thread.sleep(config.retryDelayMs * (attempt + 1))
                }
            }
        }

        logger.error("All $maxRetries attempts failed", lastException)
        return null
    }

}

private fun Int.isValidXml10CodePoint(): Boolean =
    this == 0x9 ||
        this == 0xA ||
        this == 0xD ||
        this in 0x20..0xD7FF ||
        this in 0xE000..0xFFFD ||
        this in 0x10000..0x10FFFF

private fun String.isValidRssFeedUrl(): Boolean = isNotBlank() && (startsWith("http://") || startsWith("https://"))

private fun String.isHttpUrl(): Boolean = startsWith("http://") || startsWith("https://")

private fun String?.toAbsoluteHttpUrl(baseUrl: String): String? {
    val rawUrl = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (rawUrl.startsWith("data:", ignoreCase = true)) return null

    val absoluteUrl =
        runCatching {
            URI(baseUrl).resolve(rawUrl).toString()
        }.getOrElse { rawUrl }

    return absoluteUrl.takeIf { it.isHttpUrl() }
}

private fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this.toInstant(), ZoneId.systemDefault())

private class RssFeedHttpException(
    val url: String,
    val statusCode: Int,
    val retryAfter: String?,
) : RuntimeException("HTTP $statusCode for URL: $url") {
    val isRetryable: Boolean = statusCode >= 500 || statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT
}
