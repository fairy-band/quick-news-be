package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

object MailImageUrlExtractor {
    fun findNearestCardImageUrl(
        element: Element,
        baseUrl: String,
        maxAncestorDepth: Int = DEFAULT_MAX_ANCESTOR_DEPTH,
    ): String? =
        generateSequence(element) { current -> current.parent() }
            .take(maxAncestorDepth + 1)
            .mapNotNull { container ->
                container.findLikelyImageUrl(baseUrl)
                    ?: container.findLikelyImageUrlFromNearbySiblings(baseUrl)
            }.firstOrNull()

    fun findFirstContentImageUrl(
        html: String?,
        baseUrl: String,
    ): String? {
        if (html.isNullOrBlank()) return null

        return Jsoup
            .parse(html, baseUrl)
            .select("article img, main img, .body img, .markup img, .post img")
            .asSequence()
            .filterNot { image -> image.isLikelyDecorativeImage() }
            .mapNotNull { image -> image.extractImageUrl(baseUrl) }
            .firstOrNull()
    }

    private fun Element.findLikelyImageUrl(baseUrl: String): String? =
        select("img[src], img[data-src], img[data-original], img[data-lazy-src], img[srcset]")
            .asSequence()
            .filterNot { image -> image.isLikelyDecorativeImage() }
            .mapNotNull { image -> image.extractImageUrl(baseUrl) }
            .firstOrNull()

    private fun Element.findLikelyImageUrlFromNearbySiblings(baseUrl: String): String? =
        previousElementSiblings()
            .asSequence()
            .take(MAX_NEARBY_SIBLINGS)
            .mapNotNull { sibling -> sibling.findLikelyImageUrl(baseUrl) }
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

        return absoluteUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private const val DEFAULT_MAX_ANCESTOR_DEPTH = 8
    private const val MAX_NEARBY_SIBLINGS = 3

    private val DECORATIVE_IMAGE_PARTS =
        listOf(
            "app-button",
            "avatar",
            "badge",
            "button",
            "generic-app-button",
            "icon",
            "logo",
            "pixel",
            "publish-button",
            "spacer",
            "tracking",
            "transparent",
        )
}
