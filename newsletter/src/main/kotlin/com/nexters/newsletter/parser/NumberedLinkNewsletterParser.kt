package com.nexters.newsletter.parser

abstract class NumberedLinkNewsletterParser(
    private val targetSender: String,
    private val sectionNames: Set<String>,
    private val maxArticleCount: Int = DEFAULT_MAX_ARTICLE_COUNT,
) : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(targetSender, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val normalized = content.normalizeNewsletterText()
        val issueInfo = extractIssueInfo(normalized)
        val linkMap = extractLinkMap(normalized)
        val body = normalized.substringBefore(LINKS_MARKER)
        val lines = body.lines()
        val articles = mutableListOf<MailContent>()
        var section: String? = null
        var index = 0

        while (index < lines.size) {
            val line = lines[index].trim()
            val sectionHeader = line.asSectionHeader()

            if (sectionHeader != null) {
                section = sectionHeader
                index++
                continue
            }

            val match = ARTICLE_START_REGEX.matchEntire(line)
            if (match == null || section == null || section !in sectionNames) {
                index++
                continue
            }

            val title = extractTitle(lines, index, match.groupValues[1])
            val linkNumber = match.groupValues[2]
            val block = collectArticleBlock(lines, index, match.groupValues[3])
            val description =
                block.descriptionLines
                    .joinToString(" ")
                    .cleanInlineText()
                    .take(MAX_DESCRIPTION_LENGTH)
            val link = linkMap[linkNumber]?.cleanUrl()

            if (link != null && !shouldSkip(title, description, block.rawText)) {
                articles.add(
                    MailContent(
                        title = title,
                        content = "${issueInfo.prefix()}$description",
                        link = link,
                        section = section,
                    ),
                )
            }

            index = block.nextIndex
        }

        return articles
            .distinctBy { "${it.section}:${it.title}".lowercase() }
            .take(maxArticleCount)
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

    private data class ArticleBlock(
        val descriptionLines: List<String>,
        val rawText: String,
        val nextIndex: Int,
    )

    private fun extractIssueInfo(content: String): IssueInfo {
        val match = ISSUE_REGEX.find(content)
        return IssueInfo(
            number = match?.groupValues?.getOrNull(1),
            date = match?.groupValues?.getOrNull(2),
        )
    }

    private fun extractLinkMap(content: String): Map<String, String> =
        LINK_REGEX
            .findAll(content)
            .associate { match -> match.groupValues[1] to match.groupValues[2].cleanUrl() }

    private fun extractTitle(
        lines: List<String>,
        index: Int,
        matchedTitle: String,
    ): String {
        val previousLine = lines.getOrNull(index - 1)?.trim().orEmpty()
        val prefix =
            previousLine
                .takeIf { it.isWrappedTitlePrefix() }
                ?.cleanInlineText()

        return listOfNotNull(prefix, matchedTitle.cleanInlineText())
            .joinToString(" ")
            .cleanInlineText()
    }

    private fun collectArticleBlock(
        lines: List<String>,
        startIndex: Int,
        firstDescriptionLine: String,
    ): ArticleBlock {
        val descriptionLines = mutableListOf(firstDescriptionLine)
        val rawLines = mutableListOf(lines[startIndex])
        var index = startIndex + 1

        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.isBlank() || line.asSectionHeader() != null || line.isFooterStart() || ARTICLE_START_REGEX.matches(line)) {
                break
            }

            rawLines.add(line)
            if (!line.isSkippableContinuationLine()) {
                descriptionLines.add(line)
            }
            index++
        }

        return ArticleBlock(
            descriptionLines = descriptionLines,
            rawText = rawLines.joinToString("\n"),
            nextIndex = index,
        )
    }

    private fun shouldSkip(
        title: String,
        description: String,
        block: String,
    ): Boolean =
        title.length < MIN_TITLE_LENGTH ||
            description.length < MIN_DESCRIPTION_LENGTH ||
            SPONSOR_REGEX.containsMatchIn(block) ||
            EXCLUDED_TITLE_PARTS.any { title.contains(it, ignoreCase = true) }

    private fun String.asSectionHeader(): String? {
        val cleaned = cleanInlineText().uppercase()
        return sectionNames.firstOrNull { section -> section.equals(cleaned, ignoreCase = true) }
    }

    private fun String.isWrappedTitlePrefix(): Boolean =
        isNotBlank() &&
            !ARTICLE_START_REGEX.matches(this) &&
            asSectionHeader() == null &&
            !isFooterStart() &&
            !isSkippableContinuationLine() &&
            uppercase() == this

    private fun String.isFooterStart(): Boolean =
        equals("Links:", ignoreCase = true) ||
            startsWith("Suggestions?", ignoreCase = true) ||
            startsWith("Send me your tools", ignoreCase = true) ||
            startsWith("Before I go", ignoreCase = true) ||
            startsWith("A social post", ignoreCase = true) ||
            startsWith("Unsubscribe", ignoreCase = true)

    private fun String.isSkippableContinuationLine(): Boolean =
        matches(REFERENCE_LINE_REGEX) ||
            equals("Advertisement", ignoreCase = true) ||
            startsWith("_View large image", ignoreCase = true)

    private fun String.normalizeNewsletterText(): String =
        replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u00A0", " ")
            .replace("\u200B", "")
            .replace("\u00AD", "")

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.cleanUrl(): String = trim().trimEnd(')', ']', ',', '.')

    companion object {
        private const val LINKS_MARKER = "\nLinks:\n"
        private const val MIN_TITLE_LENGTH = 4
        private const val MIN_DESCRIPTION_LENGTH = 20
        private const val MAX_DESCRIPTION_LENGTH = 1_000
        private const val DEFAULT_MAX_ARTICLE_COUNT = 30

        private val ISSUE_REGEX = Regex("""Issue\s+#?(\d+)\s*[•]\s*([A-Za-z]+ \d{1,2}, \d{4})""", RegexOption.IGNORE_CASE)
        private val LINK_REGEX = Regex("""(?m)^\[(\d+)]\s+(https?://\S+)\s*$""")
        private val ARTICLE_START_REGEX = Regex("""^(.+?)\s+\[(\d+)]\s+[—–-]\s*(.*)$""")
        private val REFERENCE_LINE_REGEX = Regex("""^\[.+]\s+\[\d+]$""")
        private val SPONSOR_REGEX = Regex("""\bSPONSOR(?:ED)?\b|\bAdvertisement\b|\bAD\b""", RegexOption.IGNORE_CASE)
        private val EXCLUDED_TITLE_PARTS =
            listOf(
                "advertise",
                "sponsor",
                "unsubscribe",
                "privacy policy",
                "web version",
                "view on web",
            )
    }
}
