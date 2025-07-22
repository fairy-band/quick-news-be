package com.nexters.newsletterfeeder.parser

/**
 * Bytes JavaScript 뉴스레터 파서
 * - JavaScript 개발 관련 뉴스와 도구들을 소개하는 뉴스레터
 * - HTML 형태로 제공되며 구조화된 섹션을 가지고 있음
 */
class BytesParser : MailParser {

    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_DOMAIN, ignoreCase = true) ||
            sender.contains(NEWSLETTER_EMAIL_PATTERN, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val htmlContent = extractHtmlContent(content) ?: return emptyList()
        val issueInfo = extractIssueInfo(htmlContent, content)

        // 더 간단한 전체 링크 추출 방식 사용
        val allLinks = HtmlTextExtractor.extractLinks(htmlContent)
            .filter { (title, url) -> isValidLink(title, url) }

        // 섹션별로 분류
        val result = mutableListOf<MailContent>()

        // The Main Thing 섹션 처리
        val mainThingLinks = findLinksInSection(htmlContent, "The Main Thing", "Cool Bits", allLinks)
        result.addAll(mainThingLinks.map { (title, url) ->
            createMailContent(title, url, issueInfo, Section.MAIN_THING, "JavaScript development article")
        })

        // Cool Bits 섹션 처리
        val coolBitsLinks = findLinksInSection(htmlContent, "Cool Bits", null, allLinks)
        result.addAll(coolBitsLinks.map { (title, url) ->
            createMailContent(title, url, issueInfo, Section.COOL_BITS, "Cool development tool or resource")
        })

        // 다른 유용한 링크들
        val otherLinks = allLinks.subtract(mainThingLinks.toSet()).subtract(coolBitsLinks.toSet()).take(5)
        result.addAll(otherLinks.map { (title, url) ->
            createMailContent(title, url, issueInfo, Section.LINKS, "Additional resource")
        })

        return result
    }

    private fun findLinksInSection(htmlContent: String, sectionName: String, nextSectionName: String?, allLinks: List<Pair<String, String>>): List<Pair<String, String>> {
        val sectionStart = htmlContent.indexOf(sectionName, ignoreCase = true)
        if (sectionStart == -1) return emptyList()

        val sectionEnd = nextSectionName?.let {
            val nextStart = htmlContent.indexOf(it, sectionStart + 1, ignoreCase = true)
            if (nextStart == -1) htmlContent.length else nextStart
        } ?: htmlContent.length

        // 안전한 substring 처리 - 범위 검사
        if (sectionStart >= sectionEnd || sectionStart >= htmlContent.length) {
            return emptyList()
        }

        val actualEnd = minOf(sectionEnd, htmlContent.length)
        val sectionContent = htmlContent.substring(sectionStart, actualEnd)

        return allLinks.filter { (title, url) ->
            sectionContent.contains(url, ignoreCase = true) || sectionContent.contains(title, ignoreCase = true)
        }
    }

    private fun extractHtmlContent(content: String): String? {
        val htmlStartMarkers = listOf(
            "Content-Type: text/html",
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

    private fun extractIssueInfo(htmlContent: String, content: String): IssueInfo {
        val issueNumber = ISSUE_NUMBER_REGEX.find(content)?.groupValues?.get(1) ?: "Unknown"
        val date = DATE_REGEX.find(content)?.value ?: getCurrentDate()

        return IssueInfo(issueNumber, date)
    }

    private fun parseMainThingSection(htmlContent: String, issueInfo: IssueInfo): List<MailContent> {
        // 더 간단한 방식으로 "The Main Thing" 섹션 찾기
        val mainThingStart = htmlContent.indexOf("The Main Thing", ignoreCase = true)
        if (mainThingStart == -1) return emptyList()

        val coolBitsStart = htmlContent.indexOf("Cool Bits", ignoreCase = true)
        val sectionEnd = if (coolBitsStart > mainThingStart) coolBitsStart else htmlContent.length

        val sectionContent = htmlContent.substring(mainThingStart, sectionEnd)

        val links = HtmlTextExtractor.extractLinks(sectionContent)
            .filter { (title, url) -> isValidLink(title, url) }

        return links.map { (title, url) ->
            createMailContent(title, url, issueInfo, Section.MAIN_THING, "Main article about JavaScript development")
        }.ifEmpty {
            // h3 태그에서 제목 추출을 시도
            val headings = HtmlTextExtractor.extractByTag(sectionContent, "h3")
            val mainTitle = headings.firstOrNull() ?: "Main Article"
            listOf(createDefaultMailContent(mainTitle, issueInfo, Section.MAIN_THING))
        }
    }

    private fun parseCoolBitsSection(htmlContent: String, issueInfo: IssueInfo): List<MailContent> {
        val coolBitsStart = htmlContent.indexOf("Cool Bits", ignoreCase = true)
        if (coolBitsStart == -1) return emptyList()

        // Cool Bits 섹션부터 끝까지 (또는 다음 주요 섹션까지)
        val sectionContent = htmlContent.substring(coolBitsStart)

        // ol/li 태그에서 아이템들 추출
        val listItems = HtmlTextExtractor.extractListItems(sectionContent)

        return listItems.flatMap { itemText ->
            HtmlTextExtractor.extractLinks("<li>$itemText</li>")
                .filter { (title, url) -> isValidLink(title, url) }
                .map { (title, url) ->
                    val description = HtmlTextExtractor.extractText(itemText).replace(title, "").trim()
                    createMailContent(title, url, issueInfo, Section.COOL_BITS, description)
                }
        }
    }

    private fun parseOtherLinks(htmlContent: String, issueInfo: IssueInfo): List<MailContent> {
        val seenUrls = mutableSetOf<String>()

        return HtmlTextExtractor.extractLinks(htmlContent)
            .filter { (title, url) ->
                isValidLink(title, url) &&
                title.length > 5 &&
                seenUrls.add(url)
            }
            .take(MAX_OTHER_LINKS)
            .map { (title, url) ->
                createMailContent(title, url, issueInfo, Section.LINKS, "Related JavaScript development resource")
            }
    }

    private fun createMailContent(
        title: String,
        url: String,
        issueInfo: IssueInfo,
        section: Section,
        description: String
    ): MailContent {
        val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): $description"
        return MailContent(
            title = title,
            content = contentText,
            link = url,
            section = section.label
        )
    }

    private fun createDefaultMailContent(
        title: String,
        issueInfo: IssueInfo,
        section: Section
    ): MailContent {
        val contentText = "[${section.label}] Issue #${issueInfo.number} (${issueInfo.date}): JavaScript development news and insights"
        return MailContent(
            title = title,
            content = contentText,
            link = "https://bytes.dev/archives/${issueInfo.number}",
            section = section.label
        )
    }

    private fun isValidLink(title: String, url: String): Boolean =
        url.startsWith("http") &&
        !isExcludedLink(url) &&
        !isSponsorContent(title) &&
        title.length > 3

    private fun isSponsorContent(text: String): Boolean =
        SPONSOR_KEYWORDS.any { keyword -> text.contains(keyword, ignoreCase = true) }

    private fun isExcludedLink(url: String): Boolean =
        EXCLUDE_PATTERNS.any { pattern -> url.contains(pattern, ignoreCase = true) }

    private fun getCurrentDate(): String = java.time.LocalDate.now().toString()

    private fun String.findHtmlStart(): Int =
        listOf("<!DOCTYPE html", "<!doctype html", "<html")
            .map { this.indexOf(it, ignoreCase = true) }
            .filter { it != -1 }
            .minOrNull() ?: 0

    private data class IssueInfo(
        val number: String,
        val date: String
    )

    private enum class Section(val label: String) {
        MAIN_THING("The Main Thing"),
        COOL_BITS("Cool Bits"),
        LINKS("Links")
    }

    companion object {
        private const val NEWSLETTER_NAME = "Bytes"
        private const val NEWSLETTER_DOMAIN = "bytes.dev"
        private const val NEWSLETTER_EMAIL_PATTERN = "ui.dev"
        private const val MAX_OTHER_LINKS = 10

        private val ISSUE_NUMBER_REGEX = Regex("#(\\d+)")
        private val DATE_REGEX = Regex("\\w+,?\\s+\\w+\\s+\\d+,?\\s+\\d{4}")

        private val MAIN_THING_SECTION_REGEX = Regex(
            """(?i)(?s)<h[1-6][^>]*>.*?The Main Thing.*?</h[1-6]>.*?(?=<h[1-6][^>]*>.*?(?:Cool Bits|Our Friends).*?</h[1-6]>|$)"""
        )

        private val COOL_BITS_SECTION_REGEX = Regex(
            """(?i)(?s)<h[1-6][^>]*>.*?Cool Bits.*?</h[1-6]>.*?(?=<h[1-6][^>]*>.*?(?:Want us to say nice things|${'$'})|$)"""
        )

        private val SPONSOR_KEYWORDS = listOf("sponsor", "sponsored", "advertisement", "ad", "WorkOS")

        private val EXCLUDE_PATTERNS = listOf(
            "unsubscribe", "convertkit", "share", "facebook.com", "linkedin.com",
            "twitter.com", "mailto:", "bytes.dev/share", "bytes.dev/advertise"
        )
    }
}
