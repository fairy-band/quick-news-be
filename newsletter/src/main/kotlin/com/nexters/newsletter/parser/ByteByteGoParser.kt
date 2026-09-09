package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ByteByteGoParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains("bytebytego@mail.beehiiv.com", ignoreCase = true) ||
            sender.contains("bytebytego", ignoreCase = true) ||
            subject?.contains("ByteByteGo", ignoreCase = true) == true

    override fun parse(context: MailParseContext): List<MailContent> {
        val html = context.htmlContent?.takeIf { it.isNotBlank() }
            ?: extractHtmlContent(context.content)
            ?: context.content

        val document = Jsoup.parse(html)
        return extractArticles(document, context.subject)
    }

    private fun extractArticles(
        document: Document,
        subject: String?,
    ): List<MailContent> {
        val results = mutableListOf<MailContent>()
        val webLink = findWebLink(document)

        val headings = document.select("h1, h2")
        val allElements = document.select("*")

        for (i in 0 until headings.size) {
            val h = headings[i]
            val title = h.text().cleanInlineText()
            if (!isValidArticleTitle(title)) {
                continue
            }

            val hIndex = allElements.indexOf(h)
            val nextHIndex = if (i + 1 < headings.size) allElements.indexOf(headings[i + 1]) else allElements.size
            if (hIndex == -1) continue

            val texts = mutableListOf<String>()
            val imgs = mutableListOf<String>()
            val links = mutableListOf<String>()

            for (idx in (hIndex + 1) until nextHIndex) {
                val node = allElements[idx]
                val tagName = node.tagName().lowercase()
                if (tagName == "img") {
                    val src = node.attr("src")
                    if (src.isNotBlank() && (src.contains("media.beehiiv.com") || src.contains("bytebytego")) && !imgs.contains(src)) {
                        imgs.add(src)
                    }
                } else if (tagName == "p" || (tagName in listOf("div", "blockquote", "li") && node.children().none { it.tagName().lowercase() in listOf("p", "div", "ul", "ol") })) {
                    val text = node.text().cleanInlineText()
                    if (text.length > 20 && !texts.contains(text) && !isExcludedText(text)) {
                        texts.add(text)
                    }
                } else if (tagName == "a") {
                    val href = node.attr("href")
                    if (href.startsWith("http") && href.contains("beehiiv.com/ss/c") && !links.contains(href)) {
                        links.add(href)
                    }
                }
            }

            val desc = texts.joinToString(" ").trim()
            if (desc.length < MIN_DESCRIPTION_LENGTH) {
                continue
            }

            val finalUrl = links.firstOrNull() ?: webLink ?: "https://blog.bytebytego.com"
            val imageUrl = imgs.firstOrNull()

            results.add(
                MailContent(
                    title = title,
                    content = desc.take(MAX_DESCRIPTION_LENGTH),
                    link = finalUrl,
                    section = "ByteByteGo",
                    imageUrl = imageUrl,
                ),
            )
        }

        return results.distinctBy { it.title.lowercase() }
    }

    private fun findWebLink(document: Document): String? {
        val link = document.select("a[href]").find {
            val text = it.text().lowercase()
            text.contains("read online") || text.contains("view in browser")
        }?.attr("href")
        return link?.takeIf { it.startsWith("http") }
    }

    private fun isValidArticleTitle(title: String): Boolean {
        if (title.length < MIN_TITLE_LENGTH) return false
        val lower = title.lowercase()
        return !EXCLUDED_TITLE_KEYWORDS.any { lower.contains(it) }
    }

    private fun isExcludedText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("unsubscribe") ||
            lower.contains("how would you rate") ||
            lower.contains("update your preferences")
    }

    private fun extractHtmlContent(content: String): String? {
        val lower = content.lowercase()
        val startIndex = lower.indexOf("<html")
        val endIndex = lower.lastIndexOf("</html>")
        return if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            content.substring(startIndex, endIndex + 7)
        } else {
            null
        }
    }

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    companion object {
        private const val MIN_TITLE_LENGTH = 5
        private const val MIN_DESCRIPTION_LENGTH = 40
        private const val MAX_DESCRIPTION_LENGTH = 2_000

        private val EXCLUDED_TITLE_KEYWORDS = listOf(
            "rate today",
            "sponsored",
            "sponsor",
            "subscribe",
            "read online",
            "refer a friend",
            "check out our",
            "join our",
            "guest post",
            "poll",
            "survey",
            "feedback",
            "how would you rate",
            "cut your qa",
            "become an ai engineer",
            "cohort-based course",
            "cohort 5",
            "last call for enrollment",
            "last chance to enroll",
            "our new book",
        )
    }
}
