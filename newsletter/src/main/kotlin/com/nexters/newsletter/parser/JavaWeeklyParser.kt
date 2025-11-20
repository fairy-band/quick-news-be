package com.nexters.newsletter.parser

import org.slf4j.LoggerFactory

class JavaWeeklyParser : MailParser {
    private val logger = LoggerFactory.getLogger(JavaWeeklyParser::class.java)

    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL, ignoreCase = true)

    override fun parse(content: String): List<MailContent> =
        try {
            val issueInfo = extractIssueInfo(content)
            val results = parseSections(content, issueInfo)

            logParsingResult(results, issueInfo)
            results
        } catch (e: Exception) {
            logger.error("Failed to parse Java Weekly email", e)
            emptyList()
        }

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private fun extractIssueInfo(content: String): IssueInfo {
        val issueMatch = ISSUE_INFO_REGEX.find(content)
        return IssueInfo(
            number = issueMatch?.groupValues?.get(1) ?: "Unknown",
            date = issueMatch?.groupValues?.get(2) ?: "Unknown date"
        )
    }

    private fun parseSections(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> =
        buildList {
            extractSection(content, SECTION_ARTICLES, SECTION_PROJECTS)
                ?.let { addAll(parseArticlesSection(it, issueInfo)) }

            extractProjectsSection(content)
                ?.let { addAll(parseProjectsSection(it, issueInfo)) }
        }

    private fun extractSection(
        content: String,
        startMarker: String,
        endMarker: String
    ): String? {
        val startIndex = content.indexOf(startMarker)
        if (startIndex == -1) return null

        val contentFromStart = content.substring(startIndex)
        val endIndex = contentFromStart.indexOf(endMarker, startMarker.length)

        return if (endIndex > 0) {
            contentFromStart.substring(0, endIndex)
        } else {
            contentFromStart
        }
    }

    private fun extractProjectsSection(content: String): String? {
        val startIndex = content.indexOf(SECTION_PROJECTS)
        if (startIndex == -1) return null

        val lines = content.substring(startIndex).lines()
        val sectionLines =
            lines.takeWhile { line ->
                val trimmed = line.trim()
                !(trimmed.length == 3 && trimmed == "---")
            }

        return sectionLines.joinToString("\n")
    }

    private fun parseArticlesSection(
        sectionContent: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val lines = sectionContent.lines()
        val seenTitles = mutableSetOf<String>()
        val results = mutableListOf<MailContent>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("* ")) {
                val title = line.removePrefix("* ").trim()
                val url = findUrlInNextLines(lines, i + 1, 5)

                if (url != null && title.length >= 10 && seenTitles.add(title)) {
                    results.add(
                        createMailContent(
                            title = title,
                            url = url,
                            section = SECTION_ARTICLES,
                            issueInfo = issueInfo
                        )
                    )
                    logger.debug("Parsed article: $title")
                }
            }
            i++
        }

        return results
    }

    private fun parseProjectsSection(
        sectionContent: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val seenTitles = mutableSetOf<String>()

        return sectionContent
            .lines()
            .mapNotNull { line ->
                val trimmedLine = line.trim()
                if (!trimmedLine.startsWith("* ")) return@mapNotNull null

                val match = PROJECT_PATTERN.find(trimmedLine)
                match?.let {
                    val title = it.groupValues[1].trim()
                    val url = it.groupValues[2].trim()

                    if (title.length >= 3 && seenTitles.add(title)) {
                        logger.debug("Parsed project: $title")
                        createMailContent(
                            title = title,
                            url = url,
                            section = SECTION_PROJECTS,
                            issueInfo = issueInfo
                        )
                    } else {
                        null
                    }
                }
            }
    }

    private fun findUrlInNextLines(
        lines: List<String>,
        startIndex: Int,
        maxLines: Int
    ): String? {
        val endIndex = minOf(startIndex + maxLines, lines.size)

        return (startIndex until endIndex)
            .asSequence()
            .map { lines[it].trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> URL_PATTERN.find(line)?.groupValues?.get(1) }
            .firstOrNull()
    }

    private fun createMailContent(
        title: String,
        url: String,
        section: String,
        issueInfo: IssueInfo
    ): MailContent =
        MailContent(
            title = title,
            content = "[$section] Issue #${issueInfo.number} (${issueInfo.date}): $title",
            link = url,
            section = section
        )

    private fun logParsingResult(
        results: List<MailContent>,
        issueInfo: IssueInfo
    ) {
        if (results.isEmpty()) {
            logger.warn("No articles parsed from Java Weekly email. Issue: ${issueInfo.number}")
        } else {
            logger.info("Successfully parsed ${results.size} articles from Java Weekly Issue #${issueInfo.number}")
        }
    }

    companion object {
        private val ISSUE_INFO_REGEX = Regex("Issue\\s*»\\s*(\\d+)\\s*/\\s*([A-Za-z]+ \\d+, \\d{4})")
        private val URL_PATTERN = Regex("<?(https?://[^>\\s]+)>?")
        private val PROJECT_PATTERN = Regex("\\*\\s*(.+?)\\s*-\\s*<?(https?://[^>\\s]+)>?")

        private const val NEWSLETTER_NAME = "Java Weekly"
        private const val NEWSLETTER_MAIL = "newsletter@libhunt.com"

        private const val SECTION_ARTICLES = "Popular News and Articles"
        private const val SECTION_PROJECTS = "Popular projects"
    }
}
