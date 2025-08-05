package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64

class IlbunParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        // Extract base64 content
        val base64Content = extractBase64Content(content)
        if (base64Content.isNullOrEmpty()) {
            return emptyList()
        }

        // Decode base64 content
        val decodedContent = decodeBase64(base64Content)
        if (decodedContent.isNullOrEmpty()) {
            return emptyList()
        }

        // Parse HTML content
        val document = Jsoup.parse(decodedContent)
        return extractArticles(document)
    }

    private fun extractBase64Content(content: String): String? {
        val base64StartMarker = "Content-Transfer-Encoding: base64"
        val startIndex = content.indexOf(base64StartMarker)
        if (startIndex == -1) return null

        var contentStartIndex = content.indexOf("\n\n", startIndex)
        if (contentStartIndex == -1) contentStartIndex = content.indexOf("\r\n\r\n", startIndex)
        if (contentStartIndex == -1) return null

        return content.substring(contentStartIndex + 2).trim()
    }

    private fun decodeBase64(base64Content: String): String? =
        try {
            val cleanBase64 = base64Content.replace("\n", "").replace("\r", "")
            String(Base64.getDecoder().decode(cleanBase64))
        } catch (e: Exception) {
            null
        }

    private fun extractArticles(document: Document): List<MailContent> {
        val results = mutableListOf<MailContent>()

        // Find article tables - each main article is in a table with white background
        val articleTables = document.select("table.stb-one-col[style*='background:#ffffff']")

        for (articleTable in articleTables) {
            // Look for the article title in center-aligned paragraphs with font-size 26px
            val titleElement =
                articleTable
                    .select(
                        "p[style*='text-align: center'] span[style*='font-size: 26px'], " +
                            "h3[style*='text-align: center'] span[style*='font-size: 26px']"
                    ).firstOrNull()

            if (titleElement == null || titleElement.text().isBlank()) {
                continue
            }

            val title = titleElement.text().trim()

            // Skip non-article titles
            if (EXCLUDED_TITLES.any { title.contains(it) } || title.length < 5) {
                continue
            }

            // Extract the article content
            // Find the content div with data-message-author-role attribute in the next sibling table
            val nextTable = articleTable.nextElementSibling()

            // Extract content
            val content =
                if (nextTable != null) {
                    extractFullArticleContent(nextTable, title)
                } else {
                    // Fallback to extracting from the table directly
                    val paragraphs = articleTable.select("p:not([style*='text-align: center'])")
                    if (paragraphs.isNotEmpty()) {
                        paragraphs.joinToString("\n") { it.text().trim() }
                    } else {
                        title // Use title as content if no paragraphs found
                    }
                }

            // Extract link if available
            val links =
                articleTable
                    .select("a[href]")
                    .filterNot { it.text().contains("일분톡 구독") || it.text().contains("이 기사 공유하기") }

            val link =
                if (links.isNotEmpty()) {
                    links.first().attr("href")
                } else {
                    DEFAULT_LINK
                }

            // Add the article to results
            results.add(
                MailContent(
                    title = title,
                    content = content.ifBlank { title },
                    link = link,
                    section = "일분톡"
                )
            )
        }

        return results.distinctBy { it.title }
    }

    private fun extractFullArticleContent(
        contentDiv: Element,
        title: String
    ): String {
        val contentBuilder = StringBuilder()

        // Extract all content elements in order
        val contentElements = contentDiv.children()

        // Skip the title and empty elements at the beginning
        var startCapturing = false
        var capturedIntro = false

        for (element in contentElements) {
            val text = element.text().trim()

            // Skip empty elements
            if (text.isBlank()) {
                continue
            }

            // Look for "무슨 일인데?" as the start marker
            if (!startCapturing && text.contains("무슨 일인데?")) {
                startCapturing = true
                capturedIntro = true
                contentBuilder.append(text).append("\n\n")
                continue
            }

            // Once we've found the intro, capture all subsequent content
            if (startCapturing) {
                // If it's a heading (h3, h4) or strong text, add it with a newline
                if (element.tagName() == "h3" || element.tagName() == "h4" || element.select("strong").isNotEmpty()) {
                    contentBuilder.append("\n").append(text).append("\n\n")
                } else {
                    // Regular paragraph
                    contentBuilder.append(text).append("\n\n")
                }
            }
        }

        // If we didn't find "무슨 일인데?", try to capture all paragraphs
        if (!capturedIntro) {
            val paragraphs = contentDiv.select("p")
            for (p in paragraphs) {
                val text = p.text().trim()
                if (text.isNotBlank() && !text.contains(title)) {
                    contentBuilder.append(text).append("\n\n")
                }
            }
        }

        return contentBuilder.toString().trim()
    }

    companion object {
        private const val NEWSLETTER_NAME = "ilbuntok"
        private const val NEWSLETTER_MAIL_ADDRESS = "ilbuntok.com"
        private const val DEFAULT_LINK = "https://www.ilbuntok.com"
        private val EXCLUDED_TITLES =
            listOf(
                "뭐가 궁금해?",
                "다음 소식들은",
                "일분톡 구독",
                "오늘의 한 줄",
                "더 많은 소식",
                "어제 소식",
                "이 기사 어때요?",
                "스크롤",
                "구독",
                "취소",
                "내 추천 포인트",
                "퀵뉴스님의 한 마디",
                "부린이를 위한 부동산"
            )
    }
}
