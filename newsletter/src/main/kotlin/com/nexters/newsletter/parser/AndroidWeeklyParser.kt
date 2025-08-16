package com.nexters.newsletter.parser

class AndroidWeeklyParser : MailParser {
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
        val plainTextStartMarker = "Plain Text:"

        val startIndex = content.indexOf(plainTextStartMarker)
        if (startIndex == -1) return content

        return content.substring(startIndex + plainTextStartMarker.length).trim()
    }

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private fun extractIssueInfo(content: String): IssueInfo {
        // Extract issue number from header (e.g., "687 August 10th, 2025" or "Android Weekly #685 🤖")
        val issueMatch = ISSUE_HEADER_REGEX.find(content) ?: ISSUE_SUBJECT_REGEX.find(content)
        val issueNumber = issueMatch?.groupValues?.get(1) ?: "Unknown"

        // Extract date (e.g., "July 27th, 2025")
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
                .associateWith { header -> content.indexOf(header.label) }
                .filterValues { it >= 0 }
                .toList()
                .sortedBy { it.second }

        if (headerPositions.isEmpty()) {
            return listOf()
        }

        return headerPositions
            .flatMapIndexed { idx, (section, start) ->
                val end = headerPositions.getOrNull(idx + 1)?.second ?: content.length
                val sectionContent = content.substring(start, end)
                parseSection(section, sectionContent, issueInfo)
            }.filter {
                // Filter out sponsored content and non-article sections
                it.section != Section.SPONSORED.label &&
                    it.section != Section.JOBS.label &&
                    it.section != Section.MERCHANDISE.label &&
                    it.section != Section.PATREON.label
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

        var currentTitle: String? = null
        var currentAuthor: String? = null
        var currentDescription = StringBuilder()
        var currentUrl: String? = null

        for (line in lines) {
            val trimmedLine = line.trim()

            // Skip empty lines and section headers
            if (trimmedLine.isBlank() || Section.fromLabel(trimmedLine) != null) {
                continue
            }

            // Skip sponsored markers
            if (trimmedLine == "Sponsored" || trimmedLine.contains("Place a sponsored post")) {
                continue
            }

            // Check if this looks like a URL
            if (URL_PATTERN.containsMatchIn(trimmedLine)) {
                currentUrl = URL_PATTERN.find(trimmedLine)?.value?.cleanUrl()

                // If we have collected enough info, save the item
                if (currentTitle != null && currentUrl != null && !seenTitles.contains(currentTitle!!)) {
                    seenTitles.add(currentTitle!!)
                    val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): ${currentAuthor ?: "Unknown"} - ${currentDescription.toString().trim()}"
                    results +=
                        MailContent(
                            title = currentTitle!!,
                            content = contentText.take(500),
                            link = currentUrl!!,
                            section = section.label
                        )
                }

                // Reset for next item
                currentTitle = null
                currentAuthor = null
                currentDescription = StringBuilder()
                currentUrl = null
                continue
            }

            // Collect title, author, and description
            when {
                currentTitle == null && isLikelyTitle(trimmedLine) -> {
                    currentTitle = trimmedLine
                }
                currentTitle != null && currentAuthor == null && isLikelyAuthor(trimmedLine) -> {
                    currentAuthor = trimmedLine
                }
                currentTitle != null -> {
                    if (currentDescription.isNotEmpty()) currentDescription.append(" ")
                    currentDescription.append(trimmedLine)
                }
            }
        }

        return results
    }

    private fun isLikelyTitle(line: String): Boolean {
        // Titles are usually longer and don't contain certain patterns
        return line.length > 5 &&
            !line.startsWith("http") &&
            !line.contains("@") &&
            !line.matches(Regex("^[A-Z][a-z]+ [A-Z][a-z]+$")) // Not just a name
    }

    private fun isLikelyAuthor(line: String): Boolean {
        // Authors are usually names
        return line.matches(Regex("^[A-Z][a-z]+ [A-Z][a-z]+.*")) ||
            line.matches(Regex("^[A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+.*"))
    }

    private fun String.cleanUrl(): String =
        this
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .trim()

    private fun String.normalizeSoftBreaks(): String =
        this
            .replace("=\r\n", "")
            .replace("=\n", "")
            .replace("=\r", "")

    companion object {
        // Issue patterns
        private val ISSUE_HEADER_REGEX = Regex("(\\d{3})\\s+[A-Za-z]+ \\d+[a-z]{2}, \\d{4}")
        private val ISSUE_SUBJECT_REGEX = Regex("Android Weekly #(\\d+)")
        private val ISSUE_DATE_REGEX = Regex("[A-Za-z]+ \\d+[a-z]{2}, \\d{4}")

        // Content patterns
        private val URL_PATTERN = Regex("https?://[^\\s]+")

        private const val NEWSLETTER_NAME = "Android Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "contact@androidweekly.net"
    }

    private enum class Section(
        val label: String
    ) {
        ARTICLES("Articles & Tutorials"),
        SPONSORED("Sponsored"),
        LIBRARIES("Libraries & Code"),
        VIDEOS("Videos & Podcasts"),
        JOBS("Jobs"),
        MERCHANDISE("MERCHANDISE"),
        PATREON("PATREON");

        companion object {
            fun fromLabel(label: String) =
                entries.firstOrNull {
                    it.label.equals(label, ignoreCase = true)
                }
        }
    }
}
