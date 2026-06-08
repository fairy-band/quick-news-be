package com.nexters.external.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI

@Service
class RepresentativeImageUrlExtractorService {
    private val objectMapper = ObjectMapper()
    private val logger = LoggerFactory.getLogger(RepresentativeImageUrlExtractorService::class.java)

    fun extractFromPage(pageUrl: String): String? {
        if (!pageUrl.isHttpUrl()) return null

        return runCatching {
            val document =
                Jsoup
                    .connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(PAGE_FETCH_TIMEOUT_MILLIS)
                    .followRedirects(true)
                    .get()

            extractFromDocument(document, pageUrl)
        }.onFailure { exception ->
            logger.debug("Failed to extract representative image from page: {}", pageUrl, exception)
        }.getOrNull()
    }

    fun extractFromHtml(
        html: String?,
        baseUrl: String,
    ): String? {
        if (html.isNullOrBlank()) return null
        val document = Jsoup.parse(html, baseUrl)
        return extractFromDocument(document, baseUrl) ?: extractFirstImageFromHtml(document, baseUrl)
    }

    private fun extractFromDocument(
        document: Document,
        baseUrl: String,
    ): String? =
        extractMetaImage(document, baseUrl)
            ?: extractImageSourceLink(document, baseUrl)
            ?: extractJsonLdImage(document, baseUrl)

    private fun extractMetaImage(
        document: Document,
        baseUrl: String,
    ): String? =
        META_IMAGE_SELECTORS
            .asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.attr("content") }
            .mapNotNull { imageUrl -> imageUrl.toAbsoluteHttpUrl(baseUrl) }
            .firstOrNull()

    private fun extractImageSourceLink(
        document: Document,
        baseUrl: String,
    ): String? =
        document
            .selectFirst("""link[rel~=(?i)\bimage_src\b][href]""")
            ?.attr("href")
            ?.toAbsoluteHttpUrl(baseUrl)

    private fun extractJsonLdImage(
        document: Document,
        baseUrl: String,
    ): String? =
        document
            .select("""script[type="application/ld+json"]""")
            .asSequence()
            .mapNotNull { script -> script.data().takeIf { it.isNotBlank() } }
            .mapNotNull { scriptData -> parseJsonLdImage(scriptData) }
            .mapNotNull { imageUrl -> imageUrl.toAbsoluteHttpUrl(baseUrl) }
            .firstOrNull()

    private fun parseJsonLdImage(scriptData: String): String? =
        runCatching {
            findImageInJsonNode(objectMapper.readTree(scriptData))
        }.getOrNull()

    private fun findImageInJsonNode(node: JsonNode?): String? {
        node ?: return null

        if (node.isArray) {
            return node.asSequence().firstNotNullOfOrNull { child -> findImageInJsonNode(child) }
        }

        if (!node.isObject) return null

        extractImageNode(node.get("image"))?.let { return it }
        node.get("@graph")?.let { graph -> findImageInJsonNode(graph)?.let { return it } }

        return null
    }

    private fun extractImageNode(node: JsonNode?): String? {
        node ?: return null

        return when {
            node.isTextual -> node.asText()
            node.isArray -> node.asSequence().firstNotNullOfOrNull { child -> extractImageNode(child) }
            node.isObject -> node.get("url")?.asText() ?: node.get("contentUrl")?.asText()
            else -> null
        }
    }

    private fun extractFirstImageFromHtml(
        document: Document,
        baseUrl: String,
    ): String? =
        document
            .select("article img, main img, .post img, .content img, img")
            .asSequence()
            .filterNot { image -> image.isLikelyDecorativeImage() }
            .mapNotNull { image -> image.extractImageUrl(baseUrl) }
            .firstOrNull()

    private fun Element.extractImageUrl(baseUrl: String): String? =
        listOf(
            attr("src"),
            attr("data-src"),
            attr("data-original"),
            attr("data-lazy-src"),
            attr("srcset").firstSrcSetUrl(),
        ).firstNotNullOfOrNull { candidate -> candidate.toAbsoluteHttpUrl(baseUrl) }

    private fun Element.isLikelyDecorativeImage(): Boolean {
        val joined =
            listOf(
                attr("src"),
                attr("alt"),
                attr("class"),
                attr("width"),
                attr("height"),
            ).joinToString(" ").lowercase()

        val width = attr("width").toIntOrNull()
        val height = attr("height").toIntOrNull()

        return DECORATIVE_IMAGE_PARTS.any { joined.contains(it) } ||
            (width != null && height != null && (width <= 4 || height <= 4))
    }

    private fun String?.firstSrcSetUrl(): String? =
        this
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.substringBefore(" ")
            ?.takeIf { it.isNotBlank() }

    private fun String?.toAbsoluteHttpUrl(baseUrl: String): String? {
        val rawUrl = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (rawUrl.startsWith("data:", ignoreCase = true)) return null

        val absoluteUrl =
            runCatching {
                URI(baseUrl).resolve(rawUrl).toString()
            }.getOrElse { rawUrl }

        return absoluteUrl.takeIf { it.isHttpUrl() }
    }

    private fun String.isHttpUrl(): Boolean = startsWith("http://") || startsWith("https://")

    companion object {
        private const val PAGE_FETCH_TIMEOUT_MILLIS = 1_500
        private const val USER_AGENT = "Mozilla/5.0 compatible; QuickNewsBot/1.0"

        private val META_IMAGE_SELECTORS =
            listOf(
                """meta[property="og:image"][content]""",
                """meta[property="og:image:url"][content]""",
                """meta[name="twitter:image"][content]""",
                """meta[name="twitter:image:src"][content]""",
            )

        private val DECORATIVE_IMAGE_PARTS =
            listOf(
                "avatar",
                "badge",
                "button",
                "icon",
                "logo",
                "pixel",
                "spacer",
                "tracking",
                "transparent",
            )
    }
}
