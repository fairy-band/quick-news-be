package com.nexters.newsletter.parser

open class NumberedLinkNewsletterParser(
    private val targetSender: String,
    private val sectionNames: Set<String>,
    private val maxArticleCount: Int = DEFAULT_MAX_ARTICLE_COUNT,
    private val aggregateSectionsAsLibraries: Set<String> = emptySet(),
) : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains(targetSender, ignoreCase = true) &&
            subject?.contains(SUBSCRIPTION_CONFIRMATION_SUBJECT, ignoreCase = true) != true

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val normalized = content.normalizeNewsletterText()
        val plainText = normalized.extractPlainTextBody()
        val issueInfo = extractIssueInfo(plainText)
        val linkMap = extractLinkMap(normalized)
        val body = plainText.substringBefore(LINKS_MARKER)
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

            if (line.isUntargetedSectionHeader()) {
                section = null
                index++
                continue
            }

            if (section == null || section !in sectionNames) {
                index++
                continue
            }

            val numberedMatch = ARTICLE_START_REGEX.matchEntire(line)
            val inlineMatch = INLINE_ARTICLE_START_REGEX.matchEntire(line)
            if (numberedMatch == null && inlineMatch == null) {
                index++
                continue
            }

            val titleSeed = numberedMatch?.groupValues?.get(1) ?: inlineMatch!!.groupValues[1]
            val title = extractTitle(lines, index, titleSeed)
            val block =
                collectArticleBlock(
                    lines = lines,
                    startIndex = index,
                    firstDescriptionLine = numberedMatch?.groupValues?.get(3) ?: inlineMatch!!.groupValues[3],
                )
            val description =
                block.descriptionLines
                    .joinToString(" ")
                    .cleanInlineText()
                    .take(MAX_DESCRIPTION_LENGTH)
            val link =
                numberedMatch
                    ?.groupValues
                    ?.get(2)
                    ?.let { linkMap[it]?.cleanUrl() }
                    ?: inlineMatch?.groupValues?.get(2)?.cleanUrl()

            if (!link.isNullOrBlank() && !shouldSkip(title, description, block.rawText)) {
                articles.add(title.toMailContent(description, link, section, issueInfo))
            }

            index = block.nextIndex
        }

        val parsedContents =
            articles
                .distinctBy { "${it.section}:${it.title}".lowercase() }
                .take(maxArticleCount)

        return WeeklyLibraryContentBuilder.groupSections(
            contents = parsedContents,
            issueNumber = issueInfo.number,
            issueDate = issueInfo.date,
            sections = aggregateSectionsAsLibraries,
        )
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

    private fun String.extractPlainTextBody(): String {
        val withoutPrefix = substringAfter(PLAIN_TEXT_MARKER, this)
        return withoutPrefix.substringBefore(HTML_MARKER)
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
            if (line.isBlank() ||
                line.asSectionHeader() != null ||
                line.isUntargetedSectionHeader() ||
                line.isFooterStart() ||
                ARTICLE_START_REGEX.matches(line) ||
                INLINE_ARTICLE_START_REGEX.matches(line)
            ) {
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

    private fun String.toMailContent(
        description: String,
        link: String,
        section: String,
        issueInfo: IssueInfo,
    ): MailContent =
        MailContent(
            title = this,
            content = "${issueInfo.prefix()}$description",
            link = link,
            section = section,
        )

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
        val cleaned = cleanSectionHeaderText()
        return sectionNames.firstOrNull { section -> section.equals(cleaned, ignoreCase = true) }
    }

    private fun String.isUntargetedSectionHeader(): Boolean = trim().startsWith(LEGACY_SECTION_PREFIX)

    private fun String.isWrappedTitlePrefix(): Boolean =
        isNotBlank() &&
            !ARTICLE_START_REGEX.matches(this) &&
            !INLINE_ARTICLE_START_REGEX.matches(this) &&
            !HORIZONTAL_RULE_REGEX.matches(this) &&
            asSectionHeader() == null &&
            !isUntargetedSectionHeader() &&
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

    private fun String.cleanSectionHeaderText(): String =
        cleanInlineText()
            .replace(LEGACY_SECTION_HEADER_PREFIX_REGEX, "")
            .removeSuffix(":")
            .trim()
            .uppercase()

    private fun String.cleanUrl(): String = trim().trimEnd(')', ']', ',', '.')

    companion object {
        private const val PLAIN_TEXT_MARKER = "Plain Text:"
        private const val HTML_MARKER = "\nHTML:"
        private const val LINKS_MARKER = "\nLinks:\n"
        private const val MIN_TITLE_LENGTH = 4
        private const val MIN_DESCRIPTION_LENGTH = 20
        private const val MAX_DESCRIPTION_LENGTH = 1_000
        private const val DEFAULT_MAX_ARTICLE_COUNT = 30
        private const val LEGACY_SECTION_PREFIX = "** "
        private const val SUBSCRIPTION_CONFIRMATION_SUBJECT = "Subscription Confirmed"

        private val ISSUE_REGEX = Regex("""Issue\s+#?(\d+)\s*[•]\s*([A-Za-z]+ \d{1,2}, \d{4})""", RegexOption.IGNORE_CASE)
        private val LINK_REGEX = Regex("""(?m)^\[(\d+)]\s+(https?://\S+)\s*$""")
        private val ARTICLE_START_REGEX = Regex("""^(.+?)\s+\[(\d+)]\s+[—–-]\s*(.*)$""")
        private val INLINE_ARTICLE_START_REGEX = Regex("""^(.+?)\s+\(\s*(https?://[^)]+)\s*\)\s+[—–-]\s*(.*)$""")
        private val REFERENCE_LINE_REGEX = Regex("""^\[.+]\s+\[\d+]$""")
        private val HORIZONTAL_RULE_REGEX = Regex("""^-{5,}$""")
        private val LEGACY_SECTION_HEADER_PREFIX_REGEX = Regex("""^\*+\s*""")
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
