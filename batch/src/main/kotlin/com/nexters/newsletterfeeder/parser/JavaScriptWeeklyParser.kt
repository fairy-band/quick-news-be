package com.nexters.newsletterfeeder.parser

/**
 * JavaScript Weekly 뉴스레터 파서
 * - JavaScript 개발 관련 주간 뉴스를 제공하는 뉴스레터
 * - 텍스트 기반이면서 구조화된 섹션을 가지고 있음
 * - 링크가 괄호 안에 포함된 형태로 제공됨
 */
class JavaScriptWeeklyParser : MailParser {

    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_EMAIL, ignoreCase = true) ||
            sender.contains(NEWSLETTER_DOMAIN, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val plainText = extractPlainTextContent(content) ?: return emptyList()
        val normalized = plainText.normalizeSoftBreaks()
        val issueInfo = extractIssueInfo(normalized)

        return parseSections(normalized, issueInfo)
    }

    private fun extractPlainTextContent(content: String): String? {
        val plainTextMarkers = listOf(
            "Content-Type: text/plain",
            "emailTextContent=",
            "Plain Text:"
        )

        for (marker in plainTextMarkers) {
            val startIndex = content.indexOf(marker, ignoreCase = true)
            if (startIndex == -1) continue

            val fromMarker = content.substring(startIndex)
            val contentStart = fromMarker.indexOf("\n\n").takeIf { it != -1 } ?: 0
            return fromMarker.substring(contentStart).trim()
        }

        return content
    }

    private fun extractIssueInfo(content: String): IssueInfo {
        val issueNumber = ISSUE_NUMBER_REGEX.find(content)?.groupValues?.get(1) ?: "Unknown"
        val date = ISSUE_DATE_REGEX.find(content)?.groupValues?.get(1) ?: getCurrentDate()

        return IssueInfo(issueNumber, date)
    }

    private fun parseSections(content: String, issueInfo: IssueInfo): List<MailContent> {
        return listOf(
            parseMainArticles(content, issueInfo),
            parseArticlesSection(content, issueInfo),
            parseCodeToolsSection(content, issueInfo),
            parseInBriefSection(content, issueInfo),
            parseReleasesSection(content, issueInfo)
        ).flatten()
    }

    private fun parseMainArticles(content: String, issueInfo: IssueInfo): List<MailContent> {
        val mainSectionEnd = findSectionBoundary(content, MAIN_SECTION_END_MARKERS)
        val mainSection = content.substring(0, mainSectionEnd)

        return MAIN_ARTICLE_REGEX.findAll(mainSection)
            .filter { !isSponsorContent(it.groupValues[1]) }
            .map { match ->
                val title = match.groupValues[1].trim()
                val url = match.groupValues[2].trim()

                createMailContent(title, url, issueInfo, Section.FEATURED)
            }
            .toList()
    }

    private fun parseArticlesSection(content: String, issueInfo: IssueInfo): List<MailContent> =
        parseSection(content, "ARTICLES AND VIDEOS", Section.ARTICLES, issueInfo)

    private fun parseCodeToolsSection(content: String, issueInfo: IssueInfo): List<MailContent> =
        parseSection(content, "CODE & TOOLS", Section.CODE_TOOLS, issueInfo)

    private fun parseInBriefSection(content: String, issueInfo: IssueInfo): List<MailContent> {
        val sectionStart = content.indexOf("IN BRIEF:")
        if (sectionStart == -1) return emptyList()

        val nextSectionStart = findSectionBoundary(content, IN_BRIEF_END_MARKERS, sectionStart)
        val sectionContent = content.substring(sectionStart, nextSectionStart)

        return BRIEF_ITEM_REGEX.findAll(sectionContent)
            .mapNotNull { match ->
                val fullText = match.groupValues[1].trim()
                extractLinkFromText(fullText)?.let { (title, url) ->
                    if (!isSponsorContent(fullText)) {
                        createMailContent(title, url, issueInfo, Section.IN_BRIEF, fullText)
                    } else null
                }
            }
            .toList()
    }

    private fun parseReleasesSection(content: String, issueInfo: IssueInfo): List<MailContent> =
        parseSection(content, "RELEASES:", Section.RELEASES, issueInfo)

    private fun parseSection(
        content: String,
        sectionMarker: String,
        section: Section,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val sectionStart = content.indexOf(sectionMarker)
        if (sectionStart == -1) return emptyList()

        val nextSectionStart = findSectionBoundary(content, SECTION_END_MARKERS, sectionStart + sectionMarker.length)
        val sectionContent = content.substring(sectionStart, nextSectionStart)

        return SECTION_ITEM_REGEX.findAll(sectionContent)
            .mapNotNull { match ->
                val fullText = match.groupValues[1].trim()
                extractLinkFromText(fullText)?.let { (title, url) ->
                    if (!isSponsorContent(fullText)) {
                        createMailContent(title, url, issueInfo, section, fullText)
                    } else null
                }
            }
            .toList()
    }

    private fun extractLinkFromText(text: String): Pair<String, String>? {
        val linkMatch = LINK_PATTERN_REGEX.find(text) ?: return null
        val title = linkMatch.groupValues[1].trim()
        val url = linkMatch.groupValues[2].trim()

        return if (title.isNotBlank() && url.isNotBlank()) title to url else null
    }

    private fun createMailContent(
        title: String,
        url: String,
        issueInfo: IssueInfo,
        section: Section,
        description: String = ""
    ): MailContent {
        val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $description"
        return MailContent(
            title = title,
            content = contentText,
            link = url,
            section = section.label
        )
    }

    private fun findSectionBoundary(
        content: String,
        markers: List<String>,
        startFrom: Int = 0
    ): Int {
        return markers.mapNotNull { marker ->
            content.indexOf(marker, startFrom).takeIf { it != -1 }
        }.minOrNull() ?: content.length
    }

    private fun isSponsorContent(text: String): Boolean =
        SPONSOR_KEYWORDS.any { keyword -> text.contains(keyword, ignoreCase = true) }

    private fun getCurrentDate(): String = java.time.LocalDate.now().toString()

    private fun String.normalizeSoftBreaks(): String =
        replace("=\r\n", "").replace("=\n", "").replace("=\r", "")

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private enum class Section(val label: String) {
        FEATURED("Featured"),
        ARTICLES("Articles & Videos"),
        CODE_TOOLS("Code & Tools"),
        IN_BRIEF("In Brief"),
        RELEASES("Releases")
    }

    companion object {
        private const val NEWSLETTER_NAME = "JavaScript Weekly"
        private const val NEWSLETTER_EMAIL = "jsw@peterc.org"
        private const val NEWSLETTER_DOMAIN = "javascriptweekly.com"

        // #745 — July 18, 2025 패턴
        private val ISSUE_NUMBER_REGEX = Regex("#(\\d+)")
        private val ISSUE_DATE_REGEX = Regex("— ([A-Za-z]+ \\d+, \\d{4})")

        // * TITLE (URL) — Description 패턴
        private val MAIN_ARTICLE_REGEX = Regex(
            """\* (.+?) \( (https?://[^)]+) \) — (.+?)(?=\n\*|\n\n|$)""",
            RegexOption.DOT_MATCHES_ALL
        )

        // * 로 시작하는 일반 항목들
        private val BRIEF_ITEM_REGEX = Regex(
            """\* (.+?)(?=\n\*|\n\n|$)""",
            RegexOption.DOT_MATCHES_ALL
        )

        private val SECTION_ITEM_REGEX = Regex(
            """(?:\* |📄 )(.+?)(?=\n(?:\*|📄)|\n\n|$)""",
            RegexOption.DOT_MATCHES_ALL
        )

        // 링크 패턴 - TITLE (URL)
        private val LINK_PATTERN_REGEX = Regex("""(.+?) \( (https?://[^)]+) \)""")

        private val MAIN_SECTION_END_MARKERS = listOf(
            "IN BRIEF:", "📖 ARTICLES AND VIDEOS", "ARTICLES AND VIDEOS"
        )

        private val IN_BRIEF_END_MARKERS = listOf(
            "RELEASES:", "📖 ARTICLES AND VIDEOS", "CODE & TOOLS"
        )

        private val SECTION_END_MARKERS = listOf(
            "📄", "📖", "🛠", "▶", "RELEASES:", "CODE & TOOLS",
            "Want us to say nice things", "Built with ❤️"
        )

        private val SPONSOR_KEYWORDS = listOf("sponsor", "sponsored", "(sponsor)", "advertisement")
    }
}
