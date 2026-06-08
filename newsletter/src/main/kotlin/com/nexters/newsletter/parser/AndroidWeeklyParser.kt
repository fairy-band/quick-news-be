package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AndroidWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> = parse(content, null, null)

    override fun parse(
        content: String,
        subject: String?,
        htmlContent: String?,
    ): List<MailContent> {
        val html = htmlContent?.takeIf { it.isNotBlank() }
        if (html != null) {
            val parsed = parseHtml(html)
            if (parsed.isNotEmpty()) return parsed
        }

        return parsePlainTextIssue(content, subject)
    }

    private fun parseHtml(html: String): List<MailContent> {
        val document = Jsoup.parse(html, DEFAULT_LINK)

        return document
            .select("a[href]")
            .asSequence()
            .mapNotNull { anchor -> anchor.toMailContent() }
            .distinctBy { it.title.lowercase() }
            .take(MAX_ARTICLE_COUNT)
            .toList()
    }

    private fun Element.toMailContent(): MailContent? {
        val title = text().cleanInlineText()
        if (title.shouldSkipTitle()) return null

        val description = findDescription() ?: return null
        if (description.shouldSkipDescription()) return null

        val href = attr("href").cleanUrl()
        if (href.isBlank()) return null
        val imageUrl = MailImageUrlExtractor.findNearestCardImageUrl(this, href)

        return MailContent(
            title = title,
            content = description,
            link = href,
            section = SECTION_ARTICLES,
            imageUrl = imageUrl,
        )
    }

    private fun Element.findDescription(): String? {
        val outerContainer = parent()?.parent()
        val directDescription =
            outerContainer
                ?.children()
                ?.firstOrNull { child -> child.tagName() == "div" && child.select("a[href]").isEmpty() }
                ?.text()
                ?.cleanInlineText()

        return directDescription?.takeIf { it.length >= MIN_DESCRIPTION_LENGTH }
    }

    private fun parsePlainTextIssue(
        content: String,
        subject: String?,
    ): List<MailContent> {
        val title = subject?.cleanInlineText()?.takeIf { it.contains("Android Weekly", ignoreCase = true) } ?: return emptyList()
        val issueNumber = ISSUE_REGEX.find(title)?.groupValues?.getOrNull(1)
        val issueUrl = issueNumber?.let { "https://androidweekly.net/issues/issue-$it" } ?: DEFAULT_LINK
        val description =
            content
                .substringAfter("Articles & Tutorials", content)
                .cleanPlainText()
                .take(MAX_PLAIN_TEXT_DESCRIPTION_LENGTH)

        return listOf(
            MailContent(
                title = title,
                content = description.ifBlank { title },
                link = issueUrl,
                section = SECTION_ARTICLES,
            ),
        )
    }

    private fun String.shouldSkipTitle(): Boolean =
        length < MIN_TITLE_LENGTH ||
            EXCLUDED_TITLE_PARTS.any { contains(it, ignoreCase = true) }

    private fun String.shouldSkipDescription(): Boolean =
        length < MIN_DESCRIPTION_LENGTH ||
            EXCLUDED_DESCRIPTION_PARTS.any { contains(it, ignoreCase = true) }

    private fun String.cleanPlainText(): String =
        replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .cleanInlineText()

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.cleanUrl(): String = trim()

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "contact@androidweekly.net"
        private const val SECTION_ARTICLES = "Articles & Tutorials"
        private const val DEFAULT_LINK = "https://androidweekly.net"

        private val ISSUE_REGEX = Regex("""#(\d+)""")
        private val EXCLUDED_TITLE_PARTS =
            listOf(
                "view in web browser",
                "sponsored",
                "advertise",
                "unsubscribe",
                "privacy policy",
                "read our new report",
                "why do mobile releases land",
            )
        private val EXCLUDED_DESCRIPTION_PARTS =
            listOf(
                "advertise to more than",
                "sponsored",
                "unsubscribe",
            )

        private const val MIN_TITLE_LENGTH = 12
        private const val MIN_DESCRIPTION_LENGTH = 30
        private const val MAX_ARTICLE_COUNT = 20
        private const val MAX_PLAIN_TEXT_DESCRIPTION_LENGTH = 1_500
    }
}
