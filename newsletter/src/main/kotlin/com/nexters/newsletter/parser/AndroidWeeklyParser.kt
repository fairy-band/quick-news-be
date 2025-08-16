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

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and section headers
            if (line.isBlank() || Section.fromLabel(line) != null) {
                i++
                continue
            }

            // Skip sponsored markers
            if (line == "Sponsored" || line.contains("Place a sponsored post")) {
                i++
                continue
            }

            // Try to find title and author on current line
            val titleMatch = TITLE_PATTERN.find(line)
            if (titleMatch != null && i + 1 < lines.size) {
                val title = titleMatch.value.trim()
                val nextLine = lines[i + 1].trim()

                // Check if next line contains author information
                if (nextLine.isNotBlank() && !nextLine.startsWith("http") && Section.fromLabel(nextLine) == null) {
                    // This is likely the author line
                    val author = nextLine

                    // Collect description (skip author line)
                    val description = collectDescription(lines, i + 2)

                    // Extract URL from description or following lines
                    val url = extractUrl(lines, i + 2) ?: ""

                    if (title.isNotBlank() && !seenTitles.contains(title) && url.isNotBlank()) {
                        seenTitles.add(title)
                        val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $author - $description"
                        results +=
                            MailContent(
                                title = title,
                                content = contentText.take(500), // Limit content length
                                link = url,
                                section = section.label
                            )
                    }

                    // Skip processed lines
                    i += 3
                    continue
                }
            }

            i++
        }

        return results
    }

    private fun extractUrl(
        lines: List<String>,
        startIndex: Int
    ): String? {
        // Look for URL in the next few lines
        for (i in startIndex until minOf(startIndex + 5, lines.size)) {
            val urlMatch = URL_PATTERN.find(lines[i])
            if (urlMatch != null) {
                return urlMatch.value.cleanUrl()
            }
        }
        return null
    }

    private fun collectDescription(
        lines: List<String>,
        from: Int
    ): String {
        val description = StringBuilder()
        var i = from

        while (i < lines.size) {
            val line = lines[i].trim()

            // Stop at boundaries
            if (line.isBlank() ||
                Section.fromLabel(line) != null ||
                line == "Sponsored" ||
                URL_PATTERN.containsMatchIn(line)
            ) {
                break
            }

            // Stop at next article title (usually followed by author)
            if (i + 1 < lines.size && isLikelyTitle(line) && isLikelyAuthor(lines[i + 1])) {
                break
            }

            if (description.isNotEmpty()) description.append(" ")
            description.append(line)
            i++
        }

        return description.toString().trim()
    }

    private fun isLikelyTitle(line: String): Boolean {
        // Titles are usually longer and don't contain certain patterns
        return line.length > 10 &&
            !line.startsWith("http") &&
            !line.contains("@") &&
            !line.matches(Regex("^[A-Z][a-z]+ [A-Z][a-z]+$")) // Not just a name
    }

    private fun isLikelyAuthor(line: String): Boolean {
        // Authors are usually names or contain certain patterns
        return line.matches(Regex("^[A-Z][a-z]+ [A-Z][a-z]+.*")) ||
            // Name pattern
            line.contains(" by ") ||
            line.contains(" on ")
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
            .replace("=", "")

    companion object {
        // Issue patterns
        private val ISSUE_HEADER_REGEX = Regex("(\\d{3})\\s+[A-Za-z]+ \\d+[a-z]{2}, \\d{4}")
        private val ISSUE_SUBJECT_REGEX = Regex("Android Weekly #(\\d+)")
        private val ISSUE_DATE_REGEX = Regex("[A-Za-z]+ \\d+[a-z]{2}, \\d{4}")

        // Content patterns
        private val TITLE_PATTERN = Regex("^[A-Z].*[a-zA-Z].*$")
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
