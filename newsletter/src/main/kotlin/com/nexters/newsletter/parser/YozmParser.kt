package com.nexters.newsletter.parser

import org.jsoup.Jsoup

class YozmParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val htmlContent = context.htmlContent
        val articles = parseHtmlArticles(htmlContent) + parseTextArticles(content)

        return articles
            .distinctBy { it.link.substringBefore("?") }
            .take(MAX_ARTICLE_COUNT)
    }

    private fun parseHtmlArticles(htmlContent: String?): List<MailContent> {
        val document = htmlContent?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it) } ?: return emptyList()

        return document
            .select("a[href]")
            .asSequence()
            .mapNotNull { anchor ->
                val url = anchor.attr("abs:href").ifBlank { anchor.attr("href") }.cleanUrl()
                val title = anchor.text().cleanInlineText()
                val description =
                    anchor
                        .parents()
                        .firstOrNull()
                        ?.text()
                        ?.cleanInlineText()
                        ?.removePrefix(title)
                        ?.trim()
                        .orEmpty()

                if (shouldSkip(title, url, description)) {
                    null
                } else {
                    MailContent(
                        title = title,
                        content = description.ifBlank { title }.take(MAX_DESCRIPTION_LENGTH),
                        link = url,
                        section = SECTION_YOZM,
                        imageUrl = MailImageUrlExtractor.findNearestCardImageUrl(anchor, url),
                    )
                }
            }.toList()
    }

    private fun parseTextArticles(content: String): List<MailContent> =
        TEXT_LINK_REGEX
            .findAll(content.normalizeNewsletterText())
            .mapNotNull { match ->
                val title = match.groupValues[1].cleanInlineText()
                val url = match.groupValues[2].cleanUrl()
                val description = match.groupValues[3].cleanInlineText()

                if (shouldSkip(title, url, description)) {
                    null
                } else {
                    MailContent(
                        title = title,
                        content = description.ifBlank { title }.take(MAX_DESCRIPTION_LENGTH),
                        link = url,
                        section = SECTION_YOZM,
                    )
                }
            }.toList()

    private fun shouldSkip(
        title: String,
        url: String,
        description: String,
    ): Boolean {
        val normalizedUrl = url.lowercase()
        val text = "$title $description"
        return title.length < MIN_TITLE_LENGTH ||
            !normalizedUrl.contains("yozm.wishket.com") ||
            !normalizedUrl.contains("/magazine/detail/") ||
            EXCLUDED_TEXT_PARTS.any { text.contains(it, ignoreCase = true) } ||
            EXCLUDED_URL_PARTS.any { normalizedUrl.contains(it) }
    }

    private fun String.normalizeNewsletterText(): String =
        replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u00A0", " ")

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.cleanUrl(): String = trim().trimEnd(')', ']', ',', '.')

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "yozm_help@wishket.com"
        private const val SECTION_YOZM = "Yozm"
        private const val MIN_TITLE_LENGTH = 6
        private const val MAX_DESCRIPTION_LENGTH = 1_000
        private const val MAX_ARTICLE_COUNT = 12

        private val TEXT_LINK_REGEX =
            Regex(
                """(?m)^\s*[-*]?\s*([^(\n]{6,}?)\s+\(\s*""" +
                    """(https?://yozm\.wishket\.com/magazine/detail/[^)\s]+)\s*\)\s*(?:[-—–]\s*)?(.*)$""",
            )
        private val EXCLUDED_TEXT_PARTS =
            listOf(
                "수신거부",
                "광고",
                "unsubscribe",
            )
        private val EXCLUDED_URL_PARTS =
            listOf(
                "unsubscribe",
                "preferences",
            )
    }
}
