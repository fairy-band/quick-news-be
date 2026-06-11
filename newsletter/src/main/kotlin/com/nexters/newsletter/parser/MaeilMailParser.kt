package com.nexters.newsletter.parser

import org.jsoup.Jsoup

class MaeilMailParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> = parse(content, null, null)

    override fun parse(
        content: String,
        subject: String?,
        htmlContent: String?,
    ): List<MailContent> {
        val title = subject.extractTitle() ?: extractTitleFromContent(content, htmlContent) ?: return emptyList()
        val body = extractBody(content, htmlContent, title).ifBlank { title }
        val link = extractPrimaryLink(content, htmlContent).ifBlank { DEFAULT_LINK }

        return listOf(
            MailContent(
                title = title,
                content = body,
                link = link,
                section = SECTION_INTERVIEW,
                imageUrl = MailImageUrlExtractor.findFirstContentImageUrl(htmlContent, link),
            ),
        )
    }

    private fun extractTitleFromContent(
        content: String,
        htmlContent: String?,
    ): String? {
        val htmlTitle =
            htmlContent
                ?.takeIf { it.isNotBlank() }
                ?.let { html ->
                    val heading = Jsoup.parse(html).selectFirst("h1, h2, strong")
                    heading?.text()?.cleanInlineText()
                }
        if (htmlTitle != null && htmlTitle.length >= MIN_TITLE_LENGTH) return htmlTitle

        return content
            .normalizeNewsletterText()
            .lineSequence()
            .map { it.cleanInlineText() }
            .firstOrNull { line -> line.length >= MIN_TITLE_LENGTH && !line.isFooterLine() }
    }

    private fun extractBody(
        content: String,
        htmlContent: String?,
        title: String,
    ): String {
        val htmlText =
            htmlContent
                ?.takeIf { it.isNotBlank() }
                ?.let { Jsoup.parse(it).body().text() }
                .orEmpty()

        val source = htmlText.ifBlank { content }
        return source
            .normalizeNewsletterText()
            .substringBeforeFooter()
            .lines()
            .map { it.cleanInlineText() }
            .filter { it.isNotBlank() }
            .filterNot { line -> line.equals(title, ignoreCase = true) }
            .filterNot { line -> line.isFooterLine() }
            .joinToString(" ")
            .take(MAX_DESCRIPTION_LENGTH)
    }

    private fun extractPrimaryLink(
        content: String,
        htmlContent: String?,
    ): String {
        val htmlLink =
            htmlContent
                ?.takeIf { it.isNotBlank() }
                ?.let { Jsoup.parse(it) }
                ?.select("a[href]")
                ?.asSequence()
                ?.map { element -> element.attr("abs:href").ifBlank { element.attr("href") }.cleanUrl() }
                ?.firstOrNull { url -> url.isContentUrl() }
        if (!htmlLink.isNullOrBlank()) return htmlLink

        return URL_REGEX
            .findAll(content)
            .map { it.value.cleanUrl() }
            .firstOrNull { it.isContentUrl() }
            .orEmpty()
    }

    private fun String?.extractTitle(): String? =
        this
            ?.replace(SUBJECT_PREFIX_REGEX, "")
            ?.cleanInlineText()
            ?.takeIf { it.length >= MIN_TITLE_LENGTH }

    private fun String.isContentUrl(): Boolean {
        val normalized = lowercase()
        return startsWith("http") &&
            !normalized.contains("unsubscribe") &&
            !normalized.contains("privacy") &&
            !normalized.contains("preferences") &&
            !normalized.contains("mailto:")
    }

    private fun String.substringBeforeFooter(): String =
        FOOTER_MARKERS.fold(this) { current, marker -> current.substringBefore(marker, current) }

    private fun String.isFooterLine(): Boolean {
        val normalized = lowercase()
        return FOOTER_MARKERS.any { marker -> normalized.contains(marker.lowercase()) }
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
        private const val NEWSLETTER_MAIL_ADDRESS = "noreply@maeil-mail.kr"
        private const val DEFAULT_LINK = "https://www.maeil-mail.kr"
        private const val SECTION_INTERVIEW = "Maeil Mail"
        private const val MIN_TITLE_LENGTH = 8
        private const val MAX_DESCRIPTION_LENGTH = 2_000

        private val SUBJECT_PREFIX_REGEX = Regex("""^\s*\[매일메일]\s*""")
        private val URL_REGEX = Regex("""https?://[^\s)<>"']+""")
        private val FOOTER_MARKERS =
            listOf(
                "수신거부",
                "unsubscribe",
                "privacy",
                "preferences",
                "메일 수신",
            )
    }
}
