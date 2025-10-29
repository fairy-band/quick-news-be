package com.nexters.newsletter.parser

import org.slf4j.LoggerFactory

class JSWeeklyParser : MailParser {
    private val logger = LoggerFactory.getLogger(JSWeeklyParser::class.java)

    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> =
        try {
            val issueInfo = extractIssueInfo(content)
            val results = parseSections(content, issueInfo)

            if (results.isEmpty()) {
                logger.warn("No articles parsed from JavaScript Weekly email. Issue: ${issueInfo.number}")
            } else {
                logger.info("Successfully parsed ${results.size} articles from JavaScript Weekly Issue #${issueInfo.number}")
            }

            results
        } catch (e: Exception) {
            logger.error("Failed to parse JavaScript Weekly email", e)
            emptyList()
        }

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private fun extractIssueInfo(content: String): IssueInfo {
        val issueMatch = ISSUE_NUMBER_REGEX.find(content)
        val issueNumber = issueMatch?.groupValues?.get(1) ?: "Unknown"

        val dateMatch = ISSUE_DATE_REGEX.find(content)
        val issueDate = dateMatch?.groupValues?.get(1) ?: "Unknown date"

        return IssueInfo(issueNumber, issueDate)
    }

    private fun parseSections(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val headerPositions =
            Section.entries
                .mapNotNull { section ->
                    val position = section.findPosition(content)
                    if (position >= 0) section to position else null
                }.sortedBy { it.second }

        if (headerPositions.isEmpty()) {
            logger.warn("No section headers found in JavaScript Weekly email")
            return emptyList()
        }

        logger.debug("Found {} sections: {}", headerPositions.size, headerPositions.map { it.first.label })

        return headerPositions
            .flatMapIndexed { idx, (section, start) ->
                val end = headerPositions.getOrNull(idx + 1)?.second ?: content.length
                val sectionContent = content.substring(start, end)
                parseSection(section, sectionContent, issueInfo)
            }.filter { it.section != Section.CLASSIFIEDS.label }
    }

    private fun parseSection(
        section: Section,
        rawText: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val lines = rawText.lines()
        val results = mutableListOf<MailContent>()
        val seenTitles = mutableSetOf<String>()

        var i = 0
        while (i < lines.size) {
            val titleMatch = TITLE_REGEX.find(lines[i])
            if (titleMatch != null && i + 1 < lines.size) {
                val urlLine = lines[i + 1]
                val urlMatch = URL_REGEX.find(urlLine)
                if (urlMatch != null) {
                    val title = titleMatch.groupValues[1].trim().cleanText()
                    val url = urlMatch.groupValues[1].trim().cleanUrl()

                    if (title.length >= 10 && seenTitles.add(title)) {
                        // Extract any text after the URL on the same line
                        val urlEndIndex = urlMatch.range.last + 1
                        val textAfterUrl =
                            if (urlEndIndex < urlLine.length) {
                                urlLine
                                    .substring(urlEndIndex)
                                    .trim()
                                    .removePrefix("—")
                                    .trim()
                            } else {
                                ""
                            }

                        val restDescription = collectDescription(lines, i + 2)
                        val fullDescription =
                            if (textAfterUrl.isNotEmpty()) {
                                "$textAfterUrl $restDescription".trim()
                            } else {
                                restDescription
                            }

                        val description = fullDescription.cleanText()
                        val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $description"
                        results += MailContent(title = title, content = contentText, link = url, section = section.label)
                    }
                    i += 2
                    continue
                }
            }
            i++
        }
        return results
    }

    private fun collectDescription(
        lines: List<String>,
        from: Int
    ): String =
        lines
            .drop(from)
            .map { it.trim() }
            .takeWhile { !isBoundaryLine(it) }
            .joinToString(" ")
            .take(200)

    private fun isBoundaryLine(line: String): Boolean =
        line.isBlank() || TITLE_REGEX.containsMatchIn(line) || URL_REGEX.containsMatchIn(line)

    private fun String.cleanUrl(): String = replace("\n", "").replace("\r", "").replace(" ", "")

    private fun String.cleanText(): String = replace("&#39;", "'").replace("&quot;", "\"").replace("&amp;", "&")

    companion object {
        private val ISSUE_NUMBER_REGEX = Regex("#(\\d+)\\s*—")
        private val ISSUE_DATE_REGEX = Regex("—\\s*([A-Za-z]+ \\d+, \\d{4})")
        private val TITLE_REGEX = Regex("^\\*\\s*(.+?)\\s*$")
        private val URL_REGEX = Regex("^\\(\\s*(https?://[^)]+)\\s*\\)")

        private const val NEWSLETTER_NAME = "JavaScript Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "jsw@peterc.org"
    }

    private enum class Section(
        val label: String,
        val patterns: List<String>
    ) {
        ARTICLES(
            "Articles",
            listOf(
                "📖 ARTICLES AND VIDEOS",
                "ARTICLES AND VIDEOS",
                "📖 Articles"
            )
        ),
        TOOLS(
            "Tools",
            listOf(
                "🛠 CODE & TOOLS",
                "🛠 CODE AND TOOLS",
                "CODE & TOOLS",
                "CODE AND TOOLS"
            )
        ),
        CLASSIFIEDS(
            "Classifieds",
            listOf(
                "📰 Classifieds",
                "Classifieds"
            )
        );

        fun findPosition(content: String): Int =
            patterns
                .map { content.indexOf(it, ignoreCase = true) }
                .firstOrNull { it >= 0 } ?: -1

        companion object {
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label }
        }
    }
}
