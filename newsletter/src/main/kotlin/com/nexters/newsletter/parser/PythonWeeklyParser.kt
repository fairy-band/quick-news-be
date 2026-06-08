package com.nexters.newsletter.parser

class PythonWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val normalized = content.normalizeNewsletterText()
        val issueNumber = ISSUE_REGEX.find(normalized)?.groupValues?.getOrNull(1)

        return ARTICLE_REGEX
            .findAll(normalized)
            .mapNotNull { match ->
                val title = match.groupValues[1].cleanInlineText()
                val url = match.groupValues[2].cleanUrl()
                val block = match.groupValues[3]
                val description = block.cleanDescription()

                if (shouldSkip(title, block, description)) {
                    null
                } else {
                    MailContent(
                        title = title,
                        content = "${issueNumber?.let { "Issue #$it: " } ?: ""}$description",
                        link = url,
                        section = findSection(normalized, match.range.first),
                    )
                }
            }.distinctBy { it.title.lowercase() }
            .take(MAX_ARTICLE_COUNT)
            .toList()
    }

    private fun findSection(
        content: String,
        articleStart: Int,
    ): String? {
        val prefix = content.substring(0, articleStart)
        return SECTION_REGEX
            .findAll(prefix)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanInlineText()
            ?.removeSurrounding("**")
    }

    private fun shouldSkip(
        title: String,
        block: String,
        description: String,
    ): Boolean =
        title.length < MIN_TITLE_LENGTH ||
            description.length < MIN_DESCRIPTION_LENGTH ||
            SPONSOR_REGEX.containsMatchIn(block)

    private fun String.cleanDescription(): String =
        substringBefore("\n#####")
            .lines()
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

    private fun String.cleanUrl(): String = trim().trimEnd(')', ',', '.')

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "rahul@pythonweekly.com"

        private val ISSUE_REGEX = Regex("""Python Weekly\s*-\s*Issue\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val SECTION_REGEX = Regex("""(?m)^#####\s+(.+?)\s*$""")
        private val ARTICLE_REGEX =
            Regex(
                """(?ms)^######\s+\[([^]]+)]\((https?://[^)]+)\)\s*(.*?)(?=^######\s+\[|\z)""",
            )
        private val SPONSOR_REGEX = Regex("""\bSPONSOR\b|\bSponsored\b""", RegexOption.IGNORE_CASE)

        private const val MIN_TITLE_LENGTH = 8
        private const val MIN_DESCRIPTION_LENGTH = 15
        private const val MAX_DESCRIPTION_LENGTH = 1_000
        private const val MAX_ARTICLE_COUNT = 20
    }
}
