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
        // Extract issue number from header (e.g., "684 July 20th, 2025")
        val issueMatch = ISSUE_HEADER_REGEX.find(content)
        val issueNumber = issueMatch?.groupValues?.get(1) ?: "Unknown"

        // Extract date (e.g., "July 20th, 2025")
        val dateMatch = ISSUE_DATE_REGEX.find(content)
        val issueDate = dateMatch?.value ?: "Unknown date"

        return IssueInfo(issueNumber, issueDate)
    }

    private fun parseSections(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // Articles & Tutorials 섹션만 파싱 (가장 중요한 섹션)
        val articlesStart = content.indexOf("Articles & Tutorials")
        if (articlesStart == -1) return results

        // 다음 섹션까지 또는 끝까지
        val nextSectionStart = findNextSectionStart(content, articlesStart + "Articles & Tutorials".length)
        val articlesEnd = nextSectionStart ?: content.length

        val articlesContent = content.substring(articlesStart, articlesEnd)

        // URL 기반으로 아티클 분리
        return parseArticlesByUrls(articlesContent, issueInfo)
    }

    private fun findNextSectionStart(
        content: String,
        fromIndex: Int
    ): Int? {
        val sections = listOf("Libraries & Code", "Videos & Podcasts", "Jobs", "Sponsored")
        return sections
            .mapNotNull { section ->
                val index = content.indexOf(section, fromIndex)
                if (index >= 0) index else null
            }.minOrNull()
    }

    private fun parseArticlesByUrls(
        content: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()
        val urls = URL_PATTERN.findAll(content).toList()

        if (urls.isEmpty()) return results

        for (i in urls.indices) {
            val url = urls[i].value.cleanUrl()

            // URL 앞의 텍스트에서 제목과 설명 추출 (간단한 방식)
            val startPos =
                if (i == 0) {
                    // 첫 번째 URL의 경우 "Articles & Tutorials" 이후부터
                    val articlesIndex = content.indexOf("Articles & Tutorials")
                    if (articlesIndex >= 0) articlesIndex + "Articles & Tutorials".length else 0
                } else {
                    urls[i - 1].range.last + 1
                }
            val endPos = urls[i].range.first

            if (startPos < endPos) {
                val textBeforeUrl = content.substring(startPos, endPos).trim()

                // 스폰서 콘텐츠 스킵
                if (textBeforeUrl.contains("Sponsored", ignoreCase = true) ||
                    textBeforeUrl.contains("Advertise", ignoreCase = true) ||
                    textBeforeUrl.contains("QA Wolf", ignoreCase = true)
                ) {
                    continue
                }

                // 간단한 제목 추출: 첫 번째 의미있는 문장
                val title = extractSimpleTitle(textBeforeUrl)

                if (title.isNotBlank() && title.length > 10) {
                    val contentText = "[Articles & Tutorials] Issue #${issueInfo.number} (${issueInfo.date}): $textBeforeUrl"
                    results +=
                        MailContent(
                            title = title,
                            content = contentText.take(500),
                            link = url,
                            section = "Articles & Tutorials"
                        )
                }
            }
        }

        return results.take(10) // 최대 10개로 제한
    }

    private fun extractSimpleTitle(text: String): String {
        // 가장 간단한 방식: 첫 번째 대문자로 시작하는 문장을 제목으로
        val sentences = text.split(Regex("[.!?]")).map { it.trim() }

        for (sentence in sentences) {
            if (sentence.length in 10..80 &&
                sentence.firstOrNull()?.isUpperCase() == true &&
                !sentence.contains("http", ignoreCase = true) &&
                sentence.count { it == ' ' } in 2..12
            ) {
                return sentence
            }
        }

        // 문장이 없으면 첫 번째 몇 단어를 제목으로
        val words = text.split(Regex("\\s+")).take(6)
        return if (words.isNotEmpty()) words.joinToString(" ") else text.take(50)
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
        private val ISSUE_DATE_REGEX = Regex("[A-Za-z]+ \\d+[a-z]{2}, \\d{4}")

        // Content patterns
        private val URL_PATTERN = Regex("https?://[^\\s]+")

        private const val NEWSLETTER_NAME = "Android Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "contact@androidweekly.net"
    }
}
