package com.nexters.newsletter.parser

import java.util.Base64

class BytesDevParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> = parse(content, null, null)

    override fun parse(
        content: String,
        subject: String?,
        htmlContent: String?,
    ): List<MailContent> {
        val normalized = content.normalizeNewsletterText()
        val issueNumber = ISSUE_REGEX.find(normalized)?.groupValues?.getOrNull(1)
        val mainArticle = extractMainArticle(normalized, subject) ?: return emptyList()
        val prefix = issueNumber?.let { "Issue #$it: " } ?: ""

        return listOf(
            MailContent(
                title = mainArticle.title,
                content = "$prefix${mainArticle.description}",
                link = mainArticle.link,
                section = SECTION_MAIN_THING,
            ),
        )
    }

    private data class MainArticle(
        val title: String,
        val description: String,
        val link: String,
    )

    private fun extractMainArticle(
        content: String,
        subject: String?,
    ): MainArticle? {
        val mainStart = content.indexOf(SECTION_MAIN_THING, ignoreCase = true)
        if (mainStart < 0) return createFallbackArticle(content, subject)

        val lines = content.substring(mainStart).lines()
        val titleIndex =
            lines
                .indices
                .firstOrNull { index ->
                    lines[index].trim().isValidTitleLine() &&
                        lines.nextNonBlankIndex(index + 1)?.let { nextIndex ->
                            lines[nextIndex].trim().matches(HORIZONTAL_RULE_REGEX)
                        } == true
                } ?: return createFallbackArticle(content, subject)

        val title = lines[titleIndex].trim().cleanInlineText()
        val underlineIndex = lines.nextNonBlankIndex(titleIndex + 1) ?: titleIndex + 1
        val body =
            lines
                .drop(underlineIndex + 1)
                .takeWhile { line -> !line.trim().isTopLevelSection() }
                .joinToString("\n")
                .cleanDescription()

        val link =
            URL_REGEX
                .find(body)
                ?.value
                ?.cleanUrl()
                ?.decodeConvertKitUrl() ?: extractArchiveLink(content)
        if (title.isBlank() || body.isBlank() || link.isBlank()) return createFallbackArticle(content, subject)

        return MainArticle(
            title = title,
            description = body,
            link = link,
        )
    }

    private fun createFallbackArticle(
        content: String,
        subject: String?,
    ): MainArticle? {
        val title =
            subject
                ?.replace(BYTES_SUBJECT_PREFIX_REGEX, "")
                ?.cleanInlineText()
                ?.takeIf { it.length >= MIN_TITLE_LENGTH } ?: return null

        val description =
            content
                .substringBefore(SECTION_MAIN_THING)
                .cleanDescription()
                .ifBlank { title }

        val link =
            extractArchiveLink(content)
                .ifBlank { DEFAULT_LINK }
                .decodeConvertKitUrl()
        return MainArticle(title = title, description = description, link = link)
    }

    private fun extractArchiveLink(content: String): String =
        ARCHIVE_LINK_REGEX
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanUrl()
            .orEmpty()

    private fun List<String>.nextNonBlankIndex(startIndex: Int): Int? =
        (startIndex until size).firstOrNull { index -> this[index].isNotBlank() }

    private fun String.isValidTitleLine(): Boolean =
        isNotBlank() &&
            !equals(SECTION_MAIN_THING, ignoreCase = true) &&
            !matches(HORIZONTAL_RULE_REGEX)

    private fun String.isTopLevelSection(): Boolean = TOP_LEVEL_SECTIONS.any { section -> equals(section, ignoreCase = true) }

    private fun String.cleanDescription(): String =
        lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .cleanInlineText()
            .take(MAX_DESCRIPTION_LENGTH)

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

    private fun String.cleanUrl(): String = replace(Regex("\\s+"), "").trim()

    private fun String.decodeConvertKitUrl(): String {
        if (!contains(CONVERTKIT_TRACKING_HOST)) return this

        return runCatching {
            val token = substringAfterLast("/")
            val decoded = String(Base64.getDecoder().decode(token))
            decoded.takeIf { it.startsWith("http") } ?: this
        }.getOrDefault(this)
    }

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "tyler@ui.dev"
        private const val SECTION_MAIN_THING = "The Main Thing"
        private const val DEFAULT_LINK = "https://bytes.dev"
        private const val CONVERTKIT_TRACKING_HOST = "click.convertkit-mail.com"

        private val ISSUE_REGEX = Regex("""Welcome to #(\d+)""")
        private val ARCHIVE_LINK_REGEX = Regex("""Welcome to #\d+\s*\(\s*(https?://[^)\s]+)""")
        private val URL_REGEX = Regex("""https?://[^\s)]+""")
        private val HORIZONTAL_RULE_REGEX = Regex("""^-{3,}$""")
        private val BYTES_SUBJECT_PREFIX_REGEX = Regex("""^Bytes:\s*""", RegexOption.IGNORE_CASE)
        private val TOP_LEVEL_SECTIONS =
            listOf(
                "Cool Bits",
                "Spot the Bug",
                "The Bottom Line",
                "Jobs",
                "Bytes",
            )

        private const val MIN_TITLE_LENGTH = 10
        private const val MAX_DESCRIPTION_LENGTH = 1_500
    }
}
