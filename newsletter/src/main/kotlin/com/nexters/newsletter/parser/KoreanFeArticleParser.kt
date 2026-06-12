package com.nexters.newsletter.parser

class KoreanFeArticleParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val subject = context.subject
        val normalized = content.normalizeNewsletterText()
        val link =
            ARTICLE_LINK_REGEX
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.cleanUrl() ?: return emptyList()
        val title = subject.cleanTitle() ?: extractFallbackTitle(normalized) ?: return emptyList()
        val description = extractDescription(normalized).ifBlank { title }

        return listOf(
            MailContent(
                title = title,
                content = description,
                link = link,
                section = SECTION_NAME,
            ),
        )
    }

    private fun extractDescription(content: String): String {
        val start = content.indexOf(INTRO_MARKER)
        if (start < 0) return ""

        val intro = content.substring(start + INTRO_MARKER.length)
        val end =
            DESCRIPTION_END_MARKERS
                .map { marker -> intro.indexOf(marker) }
                .filter { index -> index >= 0 }
                .minOrNull() ?: intro.length

        return intro
            .substring(0, end)
            .cleanBodyText()
            .take(MAX_DESCRIPTION_LENGTH)
    }

    private fun extractFallbackTitle(content: String): String? =
        content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("View this post", ignoreCase = true) &&
                    !line.startsWith("글 링크")
            }?.cleanInlineText()

    private fun String?.cleanTitle(): String? =
        this
            ?.replace(SUBJECT_PREFIX_REGEX, "")
            ?.cleanInlineText()
            ?.takeIf { it.length >= MIN_TITLE_LENGTH }

    private fun String.cleanBodyText(): String =
        lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .cleanInlineText()

    private fun String.normalizeNewsletterText(): String = replace("\r\n", "\n").replace("\r", "\n")

    private fun String.cleanInlineText(): String =
        replace(Regex("[ \\t]+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.cleanUrl(): String = trim().trimEnd(']', ')', ',')

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "kofearticle@substack.com"
        private const val SECTION_NAME = "Korean FE Article"
        private const val INTRO_MARKER = "소개"

        private val SUBJECT_PREFIX_REGEX = Regex("""^\[Korean FE Article\]\s*""")
        private val ARTICLE_LINK_REGEX = Regex("""글 링크:\s*(https?://[^\s\[]+)""")
        private val DESCRIPTION_END_MARKERS =
            listOf(
                "팀원들의 의견",
                "목차",
                "Unsubscribe",
            )

        private const val MIN_TITLE_LENGTH = 5
        private const val MAX_DESCRIPTION_LENGTH = 1_500
    }
}
