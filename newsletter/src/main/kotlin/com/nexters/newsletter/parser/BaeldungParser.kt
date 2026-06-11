package com.nexters.newsletter.parser

import org.jsoup.Jsoup

class BaeldungParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> = parse(content, null, null)

    override fun parse(
        content: String,
        subject: String?,
        htmlContent: String?,
    ): List<MailContent> {
        val normalized = content.normalizeNewsletterText()
        val issue = ISSUE_REGEX.find("${subject.orEmpty()}\n$normalized")?.groupValues?.getOrNull(1)
        val articles =
            parseMarkdownArticles(normalized, issue) +
                parseInlineArticles(normalized, issue) +
                parseHtmlArticles(htmlContent, issue)

        return articles
            .distinctBy { it.link.substringBefore("?") }
            .take(MAX_ARTICLE_COUNT)
    }

    private fun parseMarkdownArticles(
        content: String,
        issue: String?,
    ): List<MailContent> =
        MARKDOWN_LINK_REGEX
            .findAll(content)
            .mapNotNull { match ->
                val title = match.groupValues[1].cleanInlineText()
                val url = match.groupValues[2].cleanUrl()
                if (shouldSkip(title, url, "")) {
                    null
                } else {
                    MailContent(
                        title = title,
                        content = issue.prefix() + title,
                        link = url,
                        section = SECTION_BAELDUNG,
                    )
                }
            }.toList()

    private fun parseInlineArticles(
        content: String,
        issue: String?,
    ): List<MailContent> =
        INLINE_LINK_REGEX
            .findAll(content)
            .mapNotNull { match ->
                val title = match.groupValues[1].cleanInlineText()
                val url = match.groupValues[2].cleanUrl()
                val description = match.groupValues[3].cleanInlineText()
                if (shouldSkip(title, url, description)) {
                    null
                } else {
                    MailContent(
                        title = title,
                        content = "${issue.prefix()}${description.ifBlank { title }}",
                        link = url,
                        section = SECTION_BAELDUNG,
                    )
                }
            }.toList()

    private fun parseHtmlArticles(
        htmlContent: String?,
        issue: String?,
    ): List<MailContent> {
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
                        content = "${issue.prefix()}${description.ifBlank { title }}".take(MAX_DESCRIPTION_LENGTH),
                        link = url,
                        section = SECTION_BAELDUNG,
                        imageUrl = MailImageUrlExtractor.findNearestCardImageUrl(anchor, url),
                    )
                }
            }.toList()
    }

    private fun shouldSkip(
        title: String,
        url: String,
        description: String,
    ): Boolean {
        val normalizedUrl = url.lowercase()
        return title.length < MIN_TITLE_LENGTH ||
            !normalizedUrl.contains("baeldung.com") ||
            EXCLUDED_URL_PARTS.any { normalizedUrl.contains(it) } ||
            SPONSOR_REGEX.containsMatchIn("$title $description")
    }

    private fun String?.prefix(): String = this?.let { "Issue #$it: " }.orEmpty()

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
        private const val NEWSLETTER_MAIL_ADDRESS = "eugen@baeldung.com"
        private const val SECTION_BAELDUNG = "Baeldung"
        private const val MIN_TITLE_LENGTH = 8
        private const val MAX_DESCRIPTION_LENGTH = 1_000
        private const val MAX_ARTICLE_COUNT = 12

        private val ISSUE_REGEX = Regex("""Issue\s+#?(\d+)""", RegexOption.IGNORE_CASE)
        private val MARKDOWN_LINK_REGEX = Regex("""(?m)^\s*[-*]?\s*\[([^]]+)]\((https?://[^)]+baeldung\.com[^)]*)\)""")
        private val INLINE_LINK_REGEX =
            Regex("""(?m)^\s*[-*]?\s*([^(\n]{8,}?)\s+\(\s*(https?://[^)\s]+baeldung\.com[^)\s]*)\s*\)\s*(?:[-—–]\s*)?(.*)$""")
        private val SPONSOR_REGEX = Regex("""\bSPONSOR(?:ED)?\b|\bAdvertisement\b|\bcourse\b""", RegexOption.IGNORE_CASE)
        private val EXCLUDED_URL_PARTS =
            listOf(
                "unsubscribe",
                "preferences",
                "login",
                "start-here",
                "category/",
                "tag/",
                "/course",
                "/learn-",
                "/e-book",
            )
    }
}
