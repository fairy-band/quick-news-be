package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Pattern

class SwiftVincentParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        // 먼저 HTML 부분을 추출
        val htmlContent =
            extractHtmlContent(content).let {
                TextSanitizer.decodeAndSanitize(it!!)
            }

        if (htmlContent.isEmpty()) {
            println("HTML content is null or empty")
            return emptyList()
        }

        // HTML 파싱
        val document = Jsoup.parse(htmlContent)
        return extractArticles(document)
    }

    private fun extractHtmlContent(content: String): String? {
        // 여러 가지 방법으로 HTML 콘텐츠 추출 시도

        // 1. 일반적인 방법: Content-type 헤더 이후의 내용 추출
        val htmlStartMarker = "Content-type: text/html; charset=utf-8"
        val startIndex = content.indexOf(htmlStartMarker)
        if (startIndex != -1) {
            // 헤더 이후의 빈 줄 찾기
            var contentStartIndex = content.indexOf("\n\n", startIndex)
            if (contentStartIndex == -1) contentStartIndex = content.indexOf("\r\n\r\n", startIndex)
            if (contentStartIndex != -1) {
                return content.substring(contentStartIndex + 2).trim()
            }
        }

        // 2. 멀티파트 경계 이후의 HTML 부분 찾기
        val boundaryPattern = Pattern.compile("------[A-Z0-9]+")
        val matcher = boundaryPattern.matcher(content)
        var boundary: String? = null

        if (matcher.find()) {
            boundary = matcher.group()

            // 경계선 이후에 HTML 콘텐츠 찾기
            val parts = content.split(boundary)
            for (part in parts) {
                if (part.contains("Content-type: text/html", ignoreCase = true)) {
                    val htmlPartStartIndex = part.indexOf("\n\n")
                    if (htmlPartStartIndex != -1) {
                        return part.substring(htmlPartStartIndex + 2).trim()
                    }
                }
            }
        }

        // 3. <!doctype html> 또는 <html> 태그를 찾아 시작점으로 사용
        val doctypeIndex = content.indexOf("<!doctype html>", ignoreCase = true)
        if (doctypeIndex != -1) {
            return content.substring(doctypeIndex)
        }

        val htmlTagIndex = content.indexOf("<html", ignoreCase = true)
        if (htmlTagIndex != -1) {
            return content.substring(htmlTagIndex)
        }

        // 4. 마지막 수단: 전체 내용을 반환
        return content
    }

    private fun extractArticles(document: Document): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // 이메일 제목 추출
        val emailTitle = findTitle(document)

        // 내용을 담을 StringBuilder
        val contentBuilder = StringBuilder()

        // 1. 텍스트 섹션에서 내용 추출
        val textSections = document.select("table.text-section")

        // 2. 링크 추출
        var mainLink = findMainLink(document)

        // 3. 내용 추출 시도 - 여러 방법으로 시도
        var hasContent = false

        // 3.1 텍스트 섹션에서 내용 추출
        for (section in textSections) {
            val sectionText = section.text()
            if (sectionText.length < 20 ||
                EXCLUDED_CONTENT.any { sectionText.contains(it, ignoreCase = true) }
            ) {
                continue
            }

            // 단락 추출
            val paragraphs = section.select("p")
            for (p in paragraphs) {
                val text = p.text().trim() ?: ""
                if (text.isNotBlank() &&
                    !EXCLUDED_CONTENT.any { text.contains(it, ignoreCase = true) } &&
                    text.length > 10
                ) {
                    contentBuilder.append(text).append("\n\n")
                    hasContent = true
                }
            }
        }

        // 3.2 다른 방법으로 내용 추출 시도 - 모든 p 태그
        if (!hasContent) {
            val allParagraphs = document.select("p")
            for (p in allParagraphs) {
                val text = p.text().trim() ?: ""
                if (text.isNotBlank() &&
                    !EXCLUDED_CONTENT.any { text.contains(it, ignoreCase = true) } &&
                    text.length > 10
                ) {
                    contentBuilder.append(text).append("\n\n")
                    hasContent = true
                }
            }
        }

        // 3.3 마지막 수단 - 모든 텍스트 추출
        if (!hasContent) {
            val bodyText = document.body().text() ?: ""
            if (bodyText.isNotBlank()) {
                contentBuilder.append(bodyText)
                hasContent = true
            }
        }

        return listOf(
            MailContent(
                title = emailTitle,
                content = contentBuilder.toString().trim(),
                link = mainLink,
                section = "Swift with Vincent",
            ),
        )
    }

    private fun findMainLink(document: Document): String {
        // 다양한 방법으로 링크 추출 시도

        // 1. 일반적인 링크 추출
        val allLinks = document.select("a[href]")
        for (link in allLinks) {
            val href = link.attr("href")
            val linkText = link.text()

            // 제외할 링크가 아니고, 유효한 링크인 경우
            if (href.isNotEmpty() &&
                !EXCLUDED_LINK_TEXT.any { linkText.contains(it, ignoreCase = true) } &&
                !href.contains("unsubscribe", ignoreCase = true) &&
                href.startsWith("http")
            ) {
                return href
            }
        }

        // 2. 특정 패턴의 링크 추출
        for (link in allLinks) {
            val href = link.attr("href")
            if (href.contains("swiftwithvincent.com") &&
                !href.contains("unsubscribe", ignoreCase = true)
            ) {
                return href
            }
        }

        // 3. 기본 링크 반환
        return DEFAULT_LINK
    }

    private fun findTitle(document: Document): String = document.select("td.section-text-area h2").firstOrNull()?.text() ?: "No Title Found"

    companion object {
        private const val NEWSLETTER_NAME = "Swift with Vincent"
        private const val NEWSLETTER_MAIL_ADDRESS = "swiftwithvincent.com"
        private const val DEFAULT_LINK = "https://www.swiftwithvincent.com"
        private val EXCLUDED_CONTENT =
            listOf(
                "Unsubscribe",
                "E-mail sent by Vincent Pradeilles",
                "If you've enjoyed it, feel free to forward it",
                "I wish you an amazing week",
                "Advertisement",
                "Sponsors like",
                "Newsletter #",
                "That's all for this email",
                "thanks for reading",
            )
        private val EXCLUDED_LINK_TEXT =
            listOf(
                "Unsubscribe",
                "Get it now",
            )
    }
}
