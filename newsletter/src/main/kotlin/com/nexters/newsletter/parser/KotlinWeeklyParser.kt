package com.nexters.newsletter.parser

class KotlinWeeklyParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val plainText = extractPlainTextContent(content) ?: return emptyList()
        val normalized = plainText.normalizeSoftBreaks()
        val issueInfo = extractIssueInfo(normalized)
        return parseSections(normalized, issueInfo)
    }

    private fun extractPlainTextContent(content: String): String {
        val plainTextStartMarker = "Plain Text:"

        val startIndex = content.indexOf(plainTextStartMarker)
        if (startIndex == -1) return content

        return content.substring(startIndex + plainTextStartMarker.length)
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
                .associateWith { header -> header.findPosition(content) }
                .filterValues { it >= 0 }
                .toList()
                .sortedBy { it.second }

        if (headerPositions.isEmpty()) {
            return listOf()
        }

        val parsedContents =
            headerPositions
                .flatMapIndexed { idx, (section, start) ->
                    val end = headerPositions.getOrNull(idx + 1)?.second ?: content.length
                    val sectionContent = content.substring(start, end)
                    parseSection(section, sectionContent, issueInfo)
                }.filter { it.section != Section.CONTRIBUTE.label && it.section != Section.SPONSORED.label }

        return WeeklyLibraryContentBuilder.groupSections(
            contents = parsedContents,
            issueNumber = issueInfo.number,
            issueDate = issueInfo.date,
            sections = setOf(Section.LIBRARIES.label),
        )
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
            if (shouldSkipItem(section, title, url, description)) continue

            val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $description"
            results += MailContent(title = title, content = contentText, link = url, section = section.label)
        }
        return results
    }

    private fun shouldSkipItem(
        section: Section,
        title: String,
        url: String,
        description: String,
    ): Boolean =
        title.isFooterLinkTitle() ||
            url.isFooterUrl() ||
            description.isFooterDescription() ||
            (section == Section.LIBRARIES && description.isBlank())

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
            line.matches(DOMAIN_ONLY_REGEX) ||
            line.isFooterBoundary()

    private fun String.cleanUrl(): String = replace("\n", "").replace("\r", "").replace(" ", "")

    private fun String.normalizeSoftBreaks(): String = replace("=\r\n", "=").replace("=\n", "=").replace("=\r", "=")

    private fun String.isFooterLinkTitle(): Boolean {
        val normalized = lowercase()
        return FOOTER_TITLE_PARTS.any { normalized.contains(it) } ||
            normalized.startsWith("=") ||
            normalized.startsWith("** ")
    }

    private fun String.isFooterUrl(): Boolean {
        val normalized = lowercase()
        return FOOTER_URL_PARTS.any { normalized.contains(it) }
    }

    private fun String.isFooterDescription(): Boolean {
        val normalized = lowercase()
        return FOOTER_DESCRIPTION_PARTS.any { normalized.contains(it) }
    }

    private fun String.isFooterBoundary(): Boolean {
        val normalized = trim().lowercase()
        return normalized.startsWith("copyright ") ||
            normalized.startsWith("want to change how you receive") ||
            normalized.startsWith("email marketing powered") ||
            normalized.startsWith("============================================================")
    }

    companion object {
        private val ISSUE_NUMBER_REGEX = Regex("ISSUE #(\\d+)")
        private val ISSUE_DATE_REGEX = Regex("(\\d+)[a-z]{2} of [A-Za-z]+ \\d{4}")
        private val TITLE_LINK_REGEX = Regex("(.*?)\\s*\\(\\s*(https?://[^)]+)\\)\\s*$")
        private val DOMAIN_ONLY_REGEX = Regex("^[a-z0-9.-]+\\.[a-z]{2,}$")
        private val FOOTER_TITLE_PARTS =
            listOf(
                "thanks to jetbrains",
                "twitter",
                "facebook",
                "website",
                "update your preferences",
                "unsubscribe",
            )
        private val FOOTER_URL_PARTS =
            listOf(
                "list-manage.com",
                "mailchimp.com",
                "twitter.com/kotlinweekly",
                "facebook.com/kotlinweekly",
                "www.kotlinweekly.net",
                "jetbrains.com/?utm_source=kotlinweekly",
            )
        private val FOOTER_DESCRIPTION_PARTS =
            listOf(
                "copyright",
                "all rights reserved",
                "you are receiving this email",
                "want to change how you receive",
                "email marketing powered",
            )

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

        fun findPosition(content: String): Int =
            Regex("""(?m)^\s*${Regex.escape(label)}\s*$""")
                .find(content)
                ?.range
                ?.first ?: -1

        companion object {
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label }
        }
    }
}
