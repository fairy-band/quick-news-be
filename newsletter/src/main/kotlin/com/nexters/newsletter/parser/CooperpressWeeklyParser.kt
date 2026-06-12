package com.nexters.newsletter.parser

class CooperpressWeeklyParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean = TARGET_SENDERS.any { target -> sender.contains(target, ignoreCase = true) }

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val normalized = content.normalizeNewsletterText()
        val issueInfo = extractIssueInfo(normalized)

        return ARTICLE_BLOCK_REGEX
            .findAll(normalized)
            .mapNotNull { match ->
                val title = match.groupValues[1].cleanInlineText()
                val url = match.groupValues[2].cleanUrl()
                val block = match.value
                val description = match.groupValues[3].cleanDescription()

                if (shouldSkip(title, block, description)) {
                    null
                } else {
                    MailContent(
                        title = title,
                        content = "${issueInfo.prefix()}$description".ifBlank { title },
                        link = url,
                        section = "Article",
                    )
                }
            }.distinctBy { it.title.lowercase() }
            .take(MAX_ARTICLE_COUNT)
            .toList()
    }

    private data class IssueInfo(
        val number: String?,
        val date: String?,
    ) {
        fun prefix(): String =
            when {
                number != null && date != null -> "Issue #$number ($date): "
                number != null -> "Issue #$number: "
                else -> ""
            }
    }

    private fun extractIssueInfo(content: String): IssueInfo {
        val match = ISSUE_REGEX.find(content)
        return IssueInfo(
            number = match?.groupValues?.getOrNull(1),
            date = match?.groupValues?.getOrNull(2),
        )
    }

    private fun shouldSkip(
        title: String,
        block: String,
        description: String,
    ): Boolean =
        title.length < MIN_TITLE_LENGTH ||
            description.length < MIN_DESCRIPTION_LENGTH ||
            SPONSOR_REGEX.containsMatchIn(block) ||
            EXCLUDED_TITLE_PARTS.any { title.contains(it, ignoreCase = true) }

    private fun String.cleanDescription(): String =
        lines()
            .map { it.trim() }
            .takeWhile { line -> !line.isSectionBoundary() }
            .filter { line -> line.isNotBlank() }
            .filterNot { line -> line.startsWith("--") }
            .filterNot { line -> line.startsWith("→") }
            .joinToString(" ")
            .cleanInlineText()
            .take(MAX_DESCRIPTION_LENGTH)

    private fun String.isSectionBoundary(): Boolean {
        val upper = uppercase()
        return upper == "IN BRIEF:" ||
            upper.startsWith("JOBS") ||
            upper.startsWith("CODE, TOOLS") ||
            upper.startsWith("TOOLS") ||
            upper.startsWith("ELSEWHERE") ||
            matches(HORIZONTAL_RULE_REGEX)
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

    private fun String.cleanUrl(): String = replace(Regex("\\s+"), "").trim()

    companion object {
        private val TARGET_SENDERS =
            listOf(
                "frontend@cooperpress.com",
                "node@cooperpress.com",
                "postgres@cooperpress.com",
                "react@cooperpress.com",
                "peter@golangweekly.com",
            )

        private val ISSUE_REGEX = Regex("""#\s*(\d+)\s+—\s+([A-Za-z]+ \d{1,2}, \d{4})""")
        private val ARTICLE_BLOCK_REGEX =
            Regex(
                """(?ms)^\*\s+([^\n]+?)\s*\n\s*\(\s*(https?://[^)\s]+)\s*\)\s*(.*?)(?=^\*\s+|^\s*(?:⚡️\s*)?IN BRIEF:?|^\s*[📙🛠📢].*$|^\s*-{5,}\s*$|\z)""",
            )
        private val HORIZONTAL_RULE_REGEX = Regex("""^-{5,}$""")
        private val SPONSOR_REGEX = Regex("""\bSPONSOR\b|\bSponsored\b""", RegexOption.IGNORE_CASE)
        private val EXCLUDED_TITLE_PARTS =
            listOf(
                "together with",
                "advertise",
                "sponsor",
            )

        private const val MIN_TITLE_LENGTH = 10
        private const val MIN_DESCRIPTION_LENGTH = 20
        private const val MAX_DESCRIPTION_LENGTH = 1_200
        private const val MAX_ARTICLE_COUNT = 20
    }
}
