package com.nexters.newsletterfeeder.parser

class ReactStatusParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(
                NEWSLETTER_MAIL_ADDRESS,
                ignoreCase = true
            )

    override fun parse(content: String): List<MailContent> {
        val normalized = content.normalizeContent()
        val issueInfo = extractIssueInfo(normalized)

        // 섹션 구조가 있는 경우
        val sectionPositions = findSectionPositions(normalized)
        if (sectionPositions.isNotEmpty()) {
            return parseSections(normalized, issueInfo)
        }

        // 단순한 아티클만 있는 경우 (테스트용)
        return parseSimpleArticles(normalized, issueInfo)
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

    private fun parseSimpleArticles(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        return MAIN_ARTICLE_REGEX
            .findAll(content)
            .mapNotNull { match ->
                val title = match.groupValues[1].trim()
                val url = match.groupValues[2].trim().cleanUrl()
                val description = match.groupValues[3].trim()

                if (SPONSOR_REGEX.containsMatchIn(description)) return@mapNotNull null

                val contentText = "[Main] Issue #${issueInfo.number} (${issueInfo.date}): $description"
                MailContent(
                    title = title,
                    content = contentText,
                    link = url,
                    section = "Main"
                )
            }.toList()
    }

    private fun parseSections(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val sectionPositions = findSectionPositions(content)
        if (sectionPositions.isEmpty()) return emptyList()

        return sectionPositions.flatMapIndexed { index, (section, start) ->
            val end = sectionPositions.getOrNull(index + 1)?.second ?: content.length
            val sectionContent = content.substring(start, end)
            parseSection(section, sectionContent, issueInfo)
        }
    }

    private fun findSectionPositions(content: String): List<Pair<Section, Int>> {
        val positions = mutableListOf<Pair<Section, Int>>()

        // React Status 헤더가 있는지 확인
        val reactStatusMatch = Section.MAIN.pattern.find(content)
        if (reactStatusMatch != null) {
            positions.add(Section.MAIN to reactStatusMatch.range.first)
        }

        // 다른 섹션들 찾기
        Section.entries.filter { it != Section.MAIN }.forEach { section ->
            val position =
                section.pattern
                    .find(content)
                    ?.range
                    ?.first
            if (position != null) {
                positions.add(section to position)
            }
        }

        return positions.sortedBy { it.second }
    }

    private fun parseSection(
        section: Section,
        rawSectionText: String,
        issueInfo: IssueInfo
    ): List<MailContent> =
        when (section) {
            Section.MAIN -> parseMainSection(rawSectionText, issueInfo)
            else -> parseRegularSection(section, rawSectionText, issueInfo)
        }

    private fun parseMainSection(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val mainSection = content.substringBefore("IN BRIEF:")
        return MAIN_ARTICLE_REGEX
            .findAll(mainSection)
            .mapNotNull { match ->
                val title = match.groupValues[1].trim()
                val url = match.groupValues[2].trim().cleanUrl()
                val description = match.groupValues[3].trim()

                if (SPONSOR_REGEX.containsMatchIn(description)) return@mapNotNull null

                val contentText = "[${Section.MAIN.label}] Issue #${issueInfo.number} (${issueInfo.date}): $description"
                MailContent(
                    title = title,
                    content = contentText,
                    link = url,
                    section = Section.MAIN.label
                )
            }.toList()
    }

    private fun parseRegularSection(
        section: Section,
        rawSectionText: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val sectionContent = extractSectionContent(rawSectionText)
        return extractSectionItems(sectionContent).map { (title, itemText) ->
            val url = extractUrlFromText(itemText)
            val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $itemText"
            MailContent(
                title = title,
                content = contentText,
                link = url,
                section = section.label
            )
        }
    }

    private fun extractSectionContent(rawSectionText: String): String {
        val lines = rawSectionText.lines()
        return lines
            .dropWhile { line ->
                line.trim().isEmpty() || line.contains("--") || line.contains("🛠") || line.contains("📢")
            }.joinToString("\n")
    }

    private fun extractSectionItems(sectionContent: String): List<Pair<String, String>> =
        SECTION_ITEM_REGEX
            .findAll(sectionContent)
            .map { match ->
                val itemText = match.groupValues[1].trim()
                val title = extractTitleFromItem(itemText)
                title to itemText
            }.filter { it.first.isNotBlank() }
            .toList()

    private fun extractTitleFromItem(itemText: String): String =
        itemText
            .lines()
            .firstOrNull()
            ?.substringBefore("(")
            ?.substringBefore("–")
            ?.substringBefore("—")
            ?.trim()
            ?.take(100) ?: ""

    private fun extractUrlFromText(text: String): String {
        val normalized = text.normalizeContent()

        return URL_REGEX
            .find(normalized)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.cleanUrl()
            ?: GENERAL_URL_REGEX
                .find(normalized)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.cleanUrl() ?: DEFAULT_URL
    }

    private fun String.normalizeContent(): String = replace("\r\n", "\n").replace("\r", "\n")

    private fun String.cleanUrl(): String = replace("\n", "").replace("\r", "").replace(" ", "").trim()

    companion object {
        private const val NEWSLETTER_NAME = "React Status"
        private const val NEWSLETTER_MAIL_ADDRESS = "react@cooperpress.com"
        private const val DEFAULT_URL = "https://react.statuscode.com"

        // 이슈 정보 추출용 정규식
        private val ISSUE_NUMBER_REGEX = Regex("#\\s*(\\d+)")
        private val ISSUE_DATE_REGEX = Regex("(\\w+ \\d+, \\d{4})")

        // 메인 아티클 파싱용 정규식
        private val MAIN_ARTICLE_REGEX =
            Regex(
                """^\*\s*([^(\n]+?)\s*\n\(\s*(https?://[^)]+)\s*\)\s*\n\s*[—–-]\s*(.+?)(?=^\*|\Z)""",
                setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
            )

        // 섹션 아이템 파싱용 정규식
        private val SECTION_ITEM_REGEX =
            Regex(
                """^\*\s*(.+?)(?=^\*|\Z)""",
                setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
            )

        // URL 추출용 정규식
        private val URL_REGEX = Regex("""\(\s*(https?://[^)\s]+)\s*\)""")
        private val GENERAL_URL_REGEX = Regex("""(https?://[^\s)]+)""")

        // 스폰서 글 필터링용 정규식
        private val SPONSOR_REGEX = Regex("""\(SPONSOR\)|\bsponsor\b""", RegexOption.IGNORE_CASE)
    }

    private enum class Section(
        val label: String,
        val pattern: Regex
    ) {
        MAIN(
            "Main",
            Regex("""⚛️ REACT STATUS""")
        ),
        BRIEF(
            "Brief",
            Regex("""IN\s+BRIEF:\s*\n\n""")
        ),
        TOOLS(
            "Tool",
            Regex("""🛠\s*CODE,?\s*TOOLS\s*&?\s*LIBRARIES\s*\n-{15,}""")
        ),
        ELSEWHERE(
            "Elsewhere",
            Regex("""📢\s*ELSEWHERE\s+IN\s+JAVASCRIPT\s*\n-{15,}""")
        );

        companion object {
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label }
        }
    }
}
