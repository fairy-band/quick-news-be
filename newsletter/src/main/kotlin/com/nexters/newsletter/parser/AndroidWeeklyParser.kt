package com.nexters.newsletter.parser

class AndroidWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        // HTML 컨텐츠에서 직접 파싱
        val htmlContent = extractHtmlContent(content) ?: return emptyList()
        val issueInfo = extractIssueInfoFromHtml(htmlContent)

        return parseFromHtml(htmlContent, issueInfo)
    }

    private fun extractHtmlContent(content: String): String? {
        val htmlStart = content.indexOf("<html>")
        if (htmlStart == -1) return null

        return content.substring(htmlStart)
    }

    private fun extractIssueInfoFromHtml(htmlContent: String): IssueInfo {
        // HTML에서 이슈 번호와 날짜 추출
        val issueMatch = HTML_ISSUE_REGEX.find(htmlContent)
        val issueNumber = issueMatch?.groupValues?.get(1) ?: "Unknown"

        val dateMatch = HTML_DATE_REGEX.find(htmlContent)
        val issueDate = dateMatch?.groupValues?.get(1) ?: "Unknown date"

        return IssueInfo(issueNumber, issueDate)
    }

    private fun parseFromHtml(
        htmlContent: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // Articles & Tutorials 섹션 파싱
        val articlesSection = extractSection(htmlContent, "Articles & Tutorials", "Libraries & Code")
        if (articlesSection.isNotEmpty()) {
            results.addAll(parseArticlesSection(articlesSection, issueInfo))
        }

        // Libraries & Code 섹션 파싱
        val librariesSection = extractSection(htmlContent, "Libraries & Code", "Videos & Podcasts")
        if (librariesSection.isNotEmpty()) {
            results.addAll(parseLibrariesSection(librariesSection, issueInfo))
        }

        return results.take(15) // 최대 15개로 제한
    }

    private fun extractSection(
        htmlContent: String,
        startMarker: String,
        endMarker: String
    ): String {
        val startIndex = htmlContent.indexOf(startMarker)
        if (startIndex == -1) return ""

        val endIndex = htmlContent.indexOf(endMarker, startIndex)
        return if (endIndex > startIndex) {
            htmlContent.substring(startIndex, endIndex)
        } else {
            htmlContent.substring(startIndex)
        }
    }

    private fun parseArticlesSection(
        sectionHtml: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // Android Weekly의 실제 아티클 링크 패턴
        // <a href="...f6p7au8ab..." target="_blank" style="...font-weight: bold...">제목 <span class="main-url">...</span></a>
        val articlePattern =
            """<a[^>]*href="([^"]*f6p7au8ab[^"]*)"[^>]*target="_blank"[^>]*font-weight:\s*bold[^>]*>([^<]*?)<span class="main-url"[^>]*>[^<]*</span>\s*</a>"""
                .toRegex(
                    RegexOption.DOT_MATCHES_ALL
                )

        val matches = articlePattern.findAll(sectionHtml)

        for (match in matches) {
            val href = match.groupValues[1]
            val titleWithWhitespace = match.groupValues[2]
            val title = cleanText(titleWithWhitespace)

            // 스폰서 콘텐츠나 Job 관련 제외
            if (title.contains("Sponsored", ignoreCase = true) ||
                title.contains("Senior Android Engineer", ignoreCase = true) ||
                title.contains("Advertise", ignoreCase = true)
            ) {
                continue
            }

            // 제목이 너무 짧으면 스킵
            if (title.length < 10) continue

            // 해당 링크 뒤에 오는 텍스트를 찾아서 설명으로 사용
            val linkEndIndex = sectionHtml.indexOf(match.value) + match.value.length
            val nextText = sectionHtml.substring(linkEndIndex, minOf(linkEndIndex + 1000, sectionHtml.length))
            val description = extractDescription(nextText)

            val contentText = "[Articles & Tutorials] Issue #${issueInfo.number} (${issueInfo.date}): $description"
            results.add(
                MailContent(
                    title = title,
                    content = contentText,
                    link = href,
                    section = "Articles & Tutorials"
                )
            )
        }

        return results
    }

    private fun parseLibrariesSection(
        sectionHtml: String,
        issueInfo: IssueInfo
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // Libraries 섹션의 GitHub 링크나 Android Weekly 링크
        val libraryPattern = """<a[^>]*href="([^"]*(github\.com|f6p7au8ab)[^"]*)"[^>]*>([^<]+)</a>""".toRegex()
        val matches = libraryPattern.findAll(sectionHtml)

        for (match in matches) {
            val href = match.groupValues[1]
            val title = cleanText(match.groupValues[3])

            if (title.length < 3) continue

            // 해당 링크 뒤에 오는 텍스트를 찾아서 설명으로 사용
            val linkEndIndex = sectionHtml.indexOf(match.value) + match.value.length
            val nextText = sectionHtml.substring(linkEndIndex, minOf(linkEndIndex + 500, sectionHtml.length))
            val description = extractDescription(nextText)

            val contentText = "[Libraries & Code] Issue #${issueInfo.number} (${issueInfo.date}): $description"
            results.add(
                MailContent(
                    title = title,
                    content = contentText,
                    link = href,
                    section = "Libraries & Code"
                )
            )
        }

        return results
    }

    private fun extractDescription(htmlAfterLink: String): String {
        // 다양한 패턴으로 설명 텍스트 추출 시도

        // 패턴 1: <span class="main-url"> 이후의 텍스트
        val urlSpanPattern = """<span class="main-url"[^>]*>[^<]*</span>\s*([^<]+)""".toRegex()
        val urlSpanMatch = urlSpanPattern.find(htmlAfterLink)
        if (urlSpanMatch != null) {
            val desc = cleanText(urlSpanMatch.groupValues[1])
            if (desc.length > 10) return desc.take(200)
        }

        // 패턴 2: </div><div> 사이의 텍스트
        val divPattern = """</div><div[^>]*>([^<]+)""".toRegex()
        val divMatch = divPattern.find(htmlAfterLink)
        if (divMatch != null) {
            val desc = cleanText(divMatch.groupValues[1])
            if (desc.length > 10) return desc.take(200)
        }

        // 패턴 3: 첫 번째 일반 텍스트
        val textPattern = """>([^<]{20,})<""".toRegex()
        val textMatch = textPattern.find(htmlAfterLink)
        if (textMatch != null) {
            val desc = cleanText(textMatch.groupValues[1])
            if (desc.length > 10 && !desc.contains("font-") && !desc.contains("style=")) {
                return desc.take(200)
            }
        }

        return "Android development article"
    }

    private fun cleanText(text: String): String =
        text
            .replace(Regex("<[^>]*>"), "") // HTML 태그 제거
            .replace(Regex("\\s+"), " ") // 연속 공백 정리
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    companion object {
        // HTML 파싱용 정규식들
        private val HTML_ISSUE_REGEX = Regex("(\\d{3})") // 이슈 번호
        private val HTML_DATE_REGEX = Regex("([A-Za-z]+ \\d+[a-z]{2}, \\d{4})") // 날짜

        private const val NEWSLETTER_NAME = "Android Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "contact@androidweekly.net"
    }
}
