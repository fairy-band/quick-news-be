package com.nexters.newsletterfeeder.parser

/**
 * Donny Wals Swift 뉴스레터 파서
 * - Swift, iOS, SwiftUI 개발 관련 콘텐츠를 다루는 뉴스레터
 * - HTML 형태로 제공되며 개발 팁, 튜토리얼, 책 소개 등을 포함
 */
class DonnyWalsParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_AUTHOR, ignoreCase = true) ||
            sender.contains(NEWSLETTER_DOMAIN, ignoreCase = true) ||
            sender.contains(NEWSLETTER_EMAIL_PATTERN, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val htmlContent = extractHtmlContent(content) ?: return emptyList()
        val issueInfo = extractIssueInfo(htmlContent, content)

        // 매우 간단한 접근: 모든 유효한 링크를 추출하고 분류
        val allLinks =
            HtmlTextExtractor
                .extractLinks(htmlContent)
                .filter { (title, url) ->
                    url.startsWith("http") &&
                        !isExcludedLink(url) &&
                        title.isNotBlank() &&
                        title.length > 3
                }

        val result = mutableListOf<MailContent>()

        allLinks.forEach { (title, url) ->
            val section =
                when {
                    isEducationalContent(title) -> Section.EDUCATIONAL
                    htmlContent.indexOf("Other content", ignoreCase = true) != -1 &&
                        htmlContent
                            .substring(htmlContent.indexOf("Other content", ignoreCase = true))
                            .contains(title, ignoreCase = true) -> Section.RELATED_LINKS
                    else -> Section.MAIN_CONTENT
                }

            result.add(createMailContent(title, url, issueInfo, section, "Swift/iOS development resource"))
        }

        return result
    }

    private fun extractHtmlContent(content: String): String? {
        val htmlStartMarkers =
            listOf(
                "Content-Type: text/html",
                "<!doctype html",
                "<!DOCTYPE html",
                "<html"
            )

        for (marker in htmlStartMarkers) {
            val startIndex = content.indexOf(marker, ignoreCase = true)
            if (startIndex == -1) continue

            val fromMarker = content.substring(startIndex)
            val htmlStart = fromMarker.findHtmlStart()
            return fromMarker.substring(htmlStart)
        }

        return if (HtmlTextExtractor.isHtml(content)) content else null
    }

    private fun extractIssueInfo(
        htmlContent: String,
        content: String
    ): IssueInfo {
        val titleTags = HtmlTextExtractor.extractByTag(htmlContent, "title")
        val title = titleTags.firstOrNull() ?: getH1Title(htmlContent) ?: "Swift Newsletter"
        val date = DATE_REGEX.find(content)?.value ?: getCurrentDate()

        return IssueInfo(title, date)
    }

    private fun createMailContent(
        title: String,
        url: String,
        issueInfo: IssueInfo,
        section: Section,
        description: String
    ): MailContent {
        val contentText = "[${section.label}] ${issueInfo.date}: $description"
        return MailContent(
            title = title,
            content = contentText,
            link = url,
            section = section.label
        )
    }

    private fun getH1Title(htmlContent: String): String? = HtmlTextExtractor.extractByTag(htmlContent, "h1").firstOrNull()

    private fun isEducationalContent(title: String): Boolean {
        val titleLower = title.lowercase()
        return EDUCATIONAL_KEYWORDS.any { keyword -> titleLower.contains(keyword) }
    }

    private fun isExcludedLink(url: String): Boolean = EXCLUDE_PATTERNS.any { pattern -> url.contains(pattern, ignoreCase = true) }

    private fun getCurrentDate(): String =
        java.time.LocalDate
            .now()
            .toString()

    private fun String.findHtmlStart(): Int =
        listOf("<!doctype html", "<!DOCTYPE html", "<html")
            .map { this.indexOf(it, ignoreCase = true) }
            .filter { it != -1 }
            .minOrNull() ?: 0

    private data class IssueInfo(
        val title: String,
        val date: String
    )

    private enum class Section(
        val label: String
    ) {
        MAIN_CONTENT("Main Content"),
        RELATED_LINKS("Related Links"),
        EDUCATIONAL("Educational Content")
    }

    companion object {
        private const val NEWSLETTER_AUTHOR = "Donny Wals"
        private const val NEWSLETTER_DOMAIN = "donnywals.com"
        private const val NEWSLETTER_EMAIL_PATTERN = "@donnywals.com"

        // 날짜 패턴
        private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}|\\w+,?\\s+\\w+\\s+\\d+,?\\s+\\d{4}")

        private val EDUCATIONAL_KEYWORDS =
            listOf(
                "practical",
                "guide",
                "tutorial",
                "learn",
                "course",
                "book",
                "swift",
                "ios",
                "swiftui"
            )

        private val EXCLUDE_PATTERNS =
            listOf(
                "unsubscribe",
                "sendy.donnywals.com",
                "mailto:",
                "facebook.com/sharer",
                "linkedin.com/sharing",
                "twitter.com/intent",
                "donnywals.com/wp-content/uploads" // 이미지 링크 제외
            )
    }
}
