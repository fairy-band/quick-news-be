package com.nexters.newsletter.parser

import org.jsoup.Jsoup

class TLDRNewsletterParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val htmlContent = context.htmlContent
        val normalized = content.normalizeNewsletterText()
        val textArticles = parseTextArticles(normalized)
        val htmlArticles = parseHtmlArticles(htmlContent)

        return (textArticles + htmlArticles)
            .distinctBy { it.link }
            .filterNot { it.title.isExcludedTitle() || it.content.isSponsorText() }
            .take(MAX_ARTICLE_COUNT)
    }

    private data class ArticleCandidate(
        val title: String,
        val link: String,
        val description: String,
        val section: String?,
    )

    private fun parseTextArticles(content: String): List<MailContent> {
        val lines = content.lines()
        val articles = mutableListOf<MailContent>()
        var section: String? = null
        var index = 0

        while (index < lines.size) {
            val line = lines[index].trim()
            line.asSectionHeader()?.let {
                section = it
                index++
                continue
            }

            val candidate = line.toArticleCandidate(lines, index, section)
            if (candidate != null && !candidate.description.isSponsorText()) {
                articles += candidate.toMailContent()
            }

            index++
        }

        return articles
    }

    private fun String.toArticleCandidate(
        lines: List<String>,
        index: Int,
        section: String?,
    ): ArticleCandidate? {
        val match = TEXT_LINK_REGEX.matchEntire(this) ?: return null
        val title = match.groupValues[1].cleanInlineText()
        val link = match.groupValues[2].cleanUrl()
        val inlineDescription = match.groupValues[3].cleanInlineText()
        val followingDescription =
            lines
                .drop(index + 1)
                .takeWhile { line -> line.trim().isDescriptionLine() }
                .joinToString(" ")
                .cleanInlineText()
        val description = listOf(inlineDescription, followingDescription).filter { it.isNotBlank() }.joinToString(" ")

        if (title.length < MIN_TITLE_LENGTH || link.isExcludedUrl() || description.length < MIN_DESCRIPTION_LENGTH) {
            return null
        }

        return ArticleCandidate(title = title, link = link, description = description, section = section)
    }

    private fun parseHtmlArticles(htmlContent: String?): List<MailContent> {
        val document = htmlContent?.takeIf { it.isNotBlank() }?.let { Jsoup.parse(it) } ?: return emptyList()

        return document
            .select("a[href]")
            .asSequence()
            .mapNotNull { anchor ->
                val title = anchor.text().cleanInlineText()
                val link = anchor.attr("abs:href").ifBlank { anchor.attr("href") }.cleanUrl()
                val description =
                    anchor
                        .parents()
                        .firstOrNull()
                        ?.text()
                        ?.cleanInlineText()
                        ?.removePrefix(title)
                        ?.trim()
                        .orEmpty()

                if (title.length < MIN_TITLE_LENGTH || description.length < MIN_DESCRIPTION_LENGTH || link.isExcludedUrl()) {
                    null
                } else {
                    ArticleCandidate(title = title, link = link, description = description, section = null).toMailContent()
                }
            }.toList()
    }

    private fun ArticleCandidate.toMailContent(): MailContent =
        MailContent(
            title = title,
            content = description.take(MAX_DESCRIPTION_LENGTH),
            link = link,
            section = section ?: SECTION_NEWS,
        )

    private fun String.asSectionHeader(): String? {
        val cleaned =
            replace(SECTION_EMOJI_REGEX, "")
                .cleanInlineText()
                .removeSuffix(":")
        if (cleaned.length !in 4..60) return null
        if (isBlank() || URL_REGEX.containsMatchIn(this)) return null
        if (cleaned.isExcludedTitle()) return null

        return cleaned.takeIf { header -> SECTION_HINTS.any { hint -> header.contains(hint, ignoreCase = true) } }
    }

    private fun String.isDescriptionLine(): Boolean {
        val cleaned = trim()
        return cleaned.isNotBlank() &&
            !TEXT_LINK_REGEX.matches(cleaned) &&
            cleaned.asSectionHeader() == null &&
            !cleaned.isFooterLine()
    }

    private fun String.isFooterLine(): Boolean {
        val normalized = lowercase()
        return normalized.startsWith("advertise") ||
            normalized.startsWith("jobs") ||
            normalized.startsWith("unsubscribe") ||
            normalized.startsWith("sponsor")
    }

    private fun String.isExcludedTitle(): Boolean = EXCLUDED_TITLE_PARTS.any { contains(it, ignoreCase = true) }

    private fun String.isSponsorText(): Boolean = SPONSOR_REGEX.containsMatchIn(this)

    private fun String.isExcludedUrl(): Boolean {
        val normalized = lowercase()
        return !startsWith("http") ||
            EXCLUDED_URL_PARTS.any { normalized.contains(it) }
    }

    private fun String.normalizeNewsletterText(): String =
        replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u00A0", " ")
            .replace("\u200B", "")

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.cleanUrl(): String = trim().trimEnd(')', ']', ',', '.')

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "dan@tldrnewsletter.com"
        private const val SECTION_NEWS = "TLDR"
        private const val MIN_TITLE_LENGTH = 6
        private const val MIN_DESCRIPTION_LENGTH = 20
        private const val MAX_DESCRIPTION_LENGTH = 1_000
        private const val MAX_ARTICLE_COUNT = 20

        private val TEXT_LINK_REGEX = Regex("""^(?:[-*]\s*)?(.+?)\s+\(\s*(https?://[^)\s]+)\s*\)\s*(?:[-—–]\s*)?(.*)$""")
        private val URL_REGEX = Regex("""https?://\S+""")
        private val SECTION_EMOJI_REGEX = Regex("""^[^\p{L}\p{N}#]+""")
        private val SPONSOR_REGEX = Regex("""\bSPONSOR(?:ED)?\b|\bAdvertisement\b|\bPartner\b""", RegexOption.IGNORE_CASE)
        private val SECTION_HINTS =
            listOf(
                "programming",
                "ai",
                "data",
                "security",
                "big tech",
                "startup",
                "science",
                "engineering",
                "tools",
                "quick links",
            )
        private val EXCLUDED_TITLE_PARTS =
            listOf(
                "advertise",
                "sponsor",
                "job board",
                "unsubscribe",
                "manage your subscription",
            )
        private val EXCLUDED_URL_PARTS =
            listOf(
                "unsubscribe",
                "preferences",
                "advertise",
                "sponsor",
                "jobs.tldr",
                "tldr.tech/signup",
            )
    }
}
