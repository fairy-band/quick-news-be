package com.nexters.newsletter.parser

class AndroidWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val plainText = extractPlainTextContent(content) ?: return emptyList()
        val htmlContent = extractHtmlContent(content)
        val normalized = plainText.normalizeSoftBreaks()
        val issueInfo = extractIssueInfo(normalized)

        // HTML에서 링크 추출
        val htmlLinks = if (htmlContent != null) extractLinksFromHtml(htmlContent) else emptyList()

        return parseSections(normalized, issueInfo, htmlLinks)
    }

    private fun extractPlainTextContent(content: String): String? {
        val plainTextStartMarker = "Plain Text:"

        val startIndex = content.indexOf(plainTextStartMarker)
        if (startIndex == -1) return content

        return content.substring(startIndex + plainTextStartMarker.length).trim()
    }

    private fun extractHtmlContent(content: String): String? {
        val htmlStart = content.indexOf("<html>")
        if (htmlStart == -1) return null

        return content.substring(htmlStart)
    }

    private fun extractLinksFromHtml(htmlContent: String): List<String> {
        // href="..." 패턴으로 링크 추출
        val hrefPattern = Regex("""href="([^"]*)")""")
        val links =
            hrefPattern
                .findAll(htmlContent)
                .map { it.groupValues[1] }
                .filter { link ->
                    link.startsWith("http") &&
                        !link.contains("androidweekly.net", ignoreCase = true) &&
                        !link.contains("unsubscribe", ignoreCase = true) &&
                        !link.contains("twitter.com", ignoreCase = true) &&
                        !link.contains("facebook.com", ignoreCase = true) &&
                        !link.contains("mailto:", ignoreCase = true) &&
                        !link.contains("constantcontact.com", ignoreCase = true)
                }.toList()

        return links
    }

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private fun extractIssueInfo(content: String): IssueInfo {
        // Extract issue number from header (e.g., "687 August 10th, 2025")
        val issueMatch = ISSUE_HEADER_REGEX.find(content)
        val issueNumber = issueMatch?.groupValues?.get(1) ?: "Unknown"

        // Extract date (e.g., "August 10th, 2025")
        val dateMatch = ISSUE_DATE_REGEX.find(content)
        val issueDate = dateMatch?.value ?: "Unknown date"

        return IssueInfo(issueNumber, issueDate)
    }

    private fun parseSections(
        content: String,
        issueInfo: IssueInfo,
        htmlLinks: List<String>
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // Articles & Tutorials 섹션 파싱
        val articlesStart = content.indexOf("Articles & Tutorials")
        if (articlesStart >= 0) {
            val nextSectionStart = findNextSectionStart(content, articlesStart + "Articles & Tutorials".length)
            val articlesEnd = nextSectionStart ?: content.indexOf("Libraries & Code").takeIf { it > articlesStart } ?: content.length
            val articlesContent = content.substring(articlesStart, articlesEnd)
            results.addAll(parseArticlesSection(articlesContent, issueInfo, htmlLinks))
        }

        // Libraries & Code 섹션 파싱
        val librariesStart = content.indexOf("Libraries & Code")
        if (librariesStart >= 0) {
            val nextSectionStart = findNextSectionStart(content, librariesStart + "Libraries & Code".length)
            val librariesEnd = nextSectionStart ?: content.indexOf("Videos & Podcasts").takeIf { it > librariesStart } ?: content.length
            val librariesContent = content.substring(librariesStart, librariesEnd)
            results.addAll(parseLibrariesSection(librariesContent, issueInfo, htmlLinks))
        }

        return results.take(15) // 최대 15개로 제한
    }

    private fun findNextSectionStart(
        content: String,
        fromIndex: Int
    ): Int? {
        val sections = listOf("Libraries & Code", "Videos & Podcasts", "Jobs", "POST A JOB", "SPONSORED POST")
        return sections
            .mapNotNull { section ->
                val index = content.indexOf(section, fromIndex)
                if (index >= 0) index else null
            }.minOrNull()
    }

    private fun parseArticlesSection(
        content: String,
        issueInfo: IssueInfo,
        htmlLinks: List<String>
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // 저자 이름 패턴으로 아티클 분리 (Name Name 형태)
        val authorPattern =
            Regex(
                """([A-Z][a-z]+ [A-Z][a-z]+)(?:\s+[A-Z][a-z]+)?\s+(explains?|outlines?|demonstrates?|shows?|highlights?|examines?|showcases?|warns?|discusses?|created?|turns?)"""
            )
        val authorMatches = authorPattern.findAll(content).toList()

        if (authorMatches.isEmpty()) {
            // 저자 패턴이 없으면 간단한 제목 기반 파싱
            return parseByTitles(content, issueInfo, "Articles & Tutorials")
        }

        for (i in authorMatches.indices) {
            val match = authorMatches[i]
            val author = match.groupValues[1]
            val verb = match.groupValues[2]

            // 제목 찾기: 저자 앞의 텍스트에서
            val titleStart =
                if (i == 0) {
                    content.indexOf("Articles & Tutorials") + "Articles & Tutorials".length
                } else {
                    authorMatches[i - 1].range.last + 1
                }
            val titleEnd = match.range.first

            if (titleStart < titleEnd) {
                val titleText = content.substring(titleStart, titleEnd).trim()
                val title = extractTitleFromText(titleText)

                // 설명 찾기: 저자 뒤의 텍스트에서
                val descStart = match.range.last + 1
                val descEnd =
                    if (i + 1 < authorMatches.size) {
                        // 다음 저자 패턴의 제목 시작까지
                        val nextMatch = authorMatches[i + 1]
                        var nextTitleStart = nextMatch.range.first
                        // 역으로 가면서 적절한 제목 시작점 찾기
                        val textBefore = content.substring(descStart, nextTitleStart)
                        val lastSentenceEnd = textBefore.lastIndexOfAny(listOf(". ", "! ", "? "))
                        if (lastSentenceEnd > 0) descStart + lastSentenceEnd + 2 else nextTitleStart
                    } else {
                        val nextSection = content.indexOf("Libraries & Code", descStart)
                        if (nextSection > 0) nextSection else content.length
                    }

                val description =
                    if (descStart < descEnd) {
                        content
                            .substring(descStart, descEnd)
                            .trim()
                            .replace(Regex("\\s+"), " ")
                            .take(200)
                    } else {
                        ""
                    }

                if (title.isNotBlank() &&
                    title.length > 5 &&
                    !title.contains("Sponsored", ignoreCase = true)
                ) {
                    // 실제 링크 찾기 또는 플레이스홀더 사용
                    val actualLink = findBestMatchingLink(title, htmlLinks) ?: generatePlaceholderUrl(title)

                    val contentText = "[Articles & Tutorials] Issue #${issueInfo.number} (${issueInfo.date}): $author $verb $description"
                    results +=
                        MailContent(
                            title = title,
                            content = contentText.take(500),
                            link = actualLink,
                            section = "Articles & Tutorials"
                        )
                }
            }
        }

        return results
    }

    private fun parseLibrariesSection(
        content: String,
        issueInfo: IssueInfo,
        htmlLinks: List<String>
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // 라이브러리/도구는 보통 단일 이름으로 시작하고 설명이 따라옴
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }

        var i = 1 // "Libraries & Code" 라인 스킵
        while (i < lines.size) {
            val line = lines[i]

            // 라이브러리/프로젝트 이름으로 보이는 라인 (대문자로 시작, 적절한 길이)
            if (isLikelyLibraryName(line)) {
                val title = line

                // 다음 라인들에서 설명 수집
                val description = StringBuilder()
                var j = i + 1
                while (j < lines.size && j < i + 3) {
                    val nextLine = lines[j]
                    if (isLikelyLibraryName(nextLine)) break
                    description.append(nextLine).append(" ")
                    j++
                }

                if (title.isNotBlank()) {
                    // 실제 링크 찾기 또는 플레이스홀더 사용
                    val actualLink = findBestMatchingLink(title, htmlLinks) ?: generatePlaceholderUrl(title)

                    val contentText = "[Libraries & Code] Issue #${issueInfo.number} (${issueInfo.date}): ${description.toString().trim()}"
                    results +=
                        MailContent(
                            title = title,
                            content = contentText.take(500),
                            link = actualLink,
                            section = "Libraries & Code"
                        )
                }

                i = j
            } else {
                i++
            }
        }

        return results
    }

    private fun parseByTitles(
        content: String,
        issueInfo: IssueInfo,
        section: String
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // 문장 단위로 분리하여 제목으로 보이는 것들 찾기
        val sentences = content.split(Regex("[.!]")).map { it.trim() }.filter { it.isNotBlank() }

        for (sentence in sentences) {
            if (isLikelyTitle(sentence) && sentence.length > 15) {
                val contentText = "[$section] Issue #${issueInfo.number} (${issueInfo.date}): $sentence"
                results +=
                    MailContent(
                        title = sentence,
                        content = contentText.take(500),
                        link = generatePlaceholderUrl(sentence),
                        section = section
                    )

                if (results.size >= 5) break // 최대 5개
            }
        }

        return results
    }

    private fun extractTitleFromText(text: String): String {
        // 스폰서 키워드 제거
        val cleaned =
            text
                .replace(Regex("Sponsored\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim()

        // 첫 번째 완전한 문장을 제목으로
        val sentences = cleaned.split(Regex("[.!?]")).map { it.trim() }

        for (sentence in sentences) {
            if (sentence.length in 15..100 &&
                sentence.firstOrNull()?.isUpperCase() == true &&
                sentence.count { it == ' ' } in 2..15
            ) {
                return sentence
            }
        }

        // 적절한 문장이 없으면 첫 번째 몇 단어
        val words = cleaned.split(Regex("\\s+")).take(8)
        return words.joinToString(" ")
    }

    private fun isLikelyTitle(text: String): Boolean =
        text.length in 10..120 &&
            text.firstOrNull()?.isUpperCase() == true &&
            !text.contains("http", ignoreCase = true) &&
            !text.contains("Sponsored", ignoreCase = true) &&
            text.count { it == ' ' } in 1..20

    private fun isLikelyLibraryName(text: String): Boolean =
        text.length in 3..50 &&
            text.firstOrNull()?.isUpperCase() == true &&
            !text.contains(" ", ignoreCase = true) ||
            text.count { it == ' ' } <= 2 &&
            !text.contains(".", ignoreCase = true)

    private fun findBestMatchingLink(
        title: String,
        htmlLinks: List<String>
    ): String? {
        if (htmlLinks.isEmpty()) return null

        // 제목의 키워드들을 추출
        val titleWords =
            title
                .lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 3 } // 3글자 이상 단어만

        if (titleWords.isEmpty()) return htmlLinks.firstOrNull()

        // 각 링크에 대해 점수 계산
        val scoredLinks =
            htmlLinks.map { link ->
                val linkLower = link.lowercase()
                val score =
                    titleWords.sumOf { word ->
                        when {
                            linkLower.contains(word) -> 10
                            linkLower.contains(word.take(5)) -> 5 // 단어의 앞 5글자가 포함되면
                            else -> 0
                        }
                    }
                link to score
            }

        // 가장 높은 점수의 링크 반환
        val bestMatch = scoredLinks.maxByOrNull { it.second }
        return if (bestMatch?.second ?: 0 > 0) bestMatch?.first else htmlLinks.firstOrNull()
    }

    private fun generatePlaceholderUrl(title: String): String {
        // 실제 URL이 없으므로 제목 기반으로 플레이스홀더 생성
        val slug =
            title
                .lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .replace(Regex("\\s+"), "-")
                .take(50)
        return "https://androidweekly.net/articles/$slug"
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

        private const val NEWSLETTER_NAME = "Android Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "contact@androidweekly.net"
    }
}
