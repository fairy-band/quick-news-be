package com.nexters.newsletterfeeder.parser

class KotlinWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val plainText = extractPlainTextContent(content) ?: return emptyList()
        val normalized = plainText.normalizeSoftBreaks()
        val issueInfo = extractIssueInfo(normalized)
        return parseSections(normalized, issueInfo)
    }

    private fun extractPlainTextContent(content: String): String? {
        val plainTextStartMarker = "Content-Type: text/plain;"

        val startIndex = content.indexOf(plainTextStartMarker)
        if (startIndex == -1) return content

        var contentStartIndex = content.indexOf("\n\n", startIndex)
        if (contentStartIndex == -1) contentStartIndex = content.indexOf("\r\n\r\n", startIndex)
        if (contentStartIndex == -1) return content

        return content.substring(contentStartIndex + 2)
    }

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private fun extractIssueInfo(content: String): IssueInfo {
        val issueMatch = ISSUE_NUMBER_REGEX.find(content)
        val issueNumber = issueMatch?.groupValues?.get(1) ?: "Unknown"

        val dateMatch = ISSUE_DATE_REGEX.find(content)
        val issueDate = dateMatch?.value ?: "Unknown date"

        return IssueInfo(issueNumber, issueDate)
    }

    private fun parseSections(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val headerPositions =
            Section.entries
                .associateWith { header -> content.indexOf("\n${header.label}\n") }
                .filterValues { it >= 0 }
                .toList()
                .sortedBy { it.second }

        if (headerPositions.isEmpty()) {
            return listOf()
        }

        return headerPositions
            .filter { it.first != Section.CONTRIBUTE }
            .flatMapIndexed { idx, (section, start) ->
                val end = headerPositions.getOrNull(idx + 1)?.second ?: content.length
                val sectionContent = content.substring(start, end)
                parseSection(section, sectionContent, issueInfo)
            }
    }

    private fun parseSection(
        section: Section,
        rawSectionText: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val lines = rawSectionText.lines()
        val seenTitles = mutableSetOf<String>()
        val results = mutableListOf<MailContent>()

        for (index in lines.indices) {
            val match = TITLE_LINK_REGEX.find(lines[index]) ?: continue
            val title = match.groupValues[1].trim()
            val url = match.groupValues[2].trim().cleanUrl()
            if (title.matches(DOMAIN_ONLY_REGEX) || !seenTitles.add(title)) continue

            val description = collectDescription(lines, index + 1)
            val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $description"
            results += MailContent(title = title, content = contentText, link = url, section = section.label)
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

    private fun isBoundaryLine(line: String): Boolean =
        line.isBlank() ||
            TITLE_LINK_REGEX.containsMatchIn(line) ||
            Section.fromLabel(line) != null ||
            line.matches(DOMAIN_ONLY_REGEX)

    private fun String.cleanUrl(): String = replace("\n", "").replace("\r", "").replace(" ", "")

    private fun String.normalizeSoftBreaks(): String = replace("=\r\n", "=").replace("=\n", "=").replace("=\r", "=")

    companion object {
        private val ISSUE_NUMBER_REGEX = Regex("ISSUE #(\\d+)")
        private val ISSUE_DATE_REGEX = Regex("(\\d+)[a-z]{2} of [A-Za-z]+ \\d{4}")
        private val TITLE_LINK_REGEX = Regex("(.*?)\\s*\\(\\s*(https?://[^)]+)\\)\\s*$")
        private val DOMAIN_ONLY_REGEX = Regex("^[a-z0-9.-]+\\.[a-z]{2,}$")

        private const val NEWSLETTER_NAME = "Kotlin Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "kotlinweekly.net"
    }

    private enum class Section(
        val label: String
    ) {
        ANNOUNCEMENTS("Announcements"),
        ARTICLES("Articles"),
        SPONSORED("Sponsored"),
        ANDROID("Android"),
        PODCAST("Podcast"),
        CONFERENCES("Conferences"),
        LIBRARIES("Libraries"),
        CONTRIBUTE("Contribute");

        companion object {
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label }
        }
    }
}
