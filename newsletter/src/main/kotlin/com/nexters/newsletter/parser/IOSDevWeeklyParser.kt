package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class IOSDevWeeklyParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains("iosdevweekly", ignoreCase = true) ||
            sender.contains("dave verwer", ignoreCase = true) ||
            subject?.contains("iOS Dev Weekly", ignoreCase = true) == true

    override fun parse(context: MailParseContext): List<MailContent> {
        val html = context.htmlContent?.takeIf { it.isNotBlank() }
            ?: extractHtmlContent(context.content)
            ?: context.content

        val document = Jsoup.parse(html)
        return extractArticles(document)
    }

    private fun extractArticles(document: Document): List<MailContent> {
        val sectionTables = document.select("table[role=heading]")
        val result = mutableListOf<MailContent>()

        for (secTable in sectionTables) {
            val secName = secTable.text().trim()
            if (EXCLUDED_SECTIONS.any { secName.contains(it, ignoreCase = true) }) {
                continue
            }

            var curr: Element? = secTable.nextElementSibling()
            while (curr != null) {
                if (curr.tagName() == "table" && curr.attr("role") == "heading") {
                    break
                }

                if (curr.tagName() == "a" && curr.hasAttr("href")) {
                    val href = curr.attr("href").trim()
                    val title = curr.text().cleanInlineText()

                    if (isValidArticle(title, href)) {
                        var nextElem: Element? = curr.nextElementSibling()
                        while (nextElem != null && nextElem.tagName() != "p" && nextElem.tagName() != "a" && !(nextElem.tagName() == "table" && nextElem.attr("role") == "heading")) {
                            nextElem = nextElem.nextElementSibling()
                        }

                        val desc = if (nextElem != null && nextElem.tagName() == "p") {
                            nextElem.text().cleanInlineText()
                        } else {
                            title
                        }

                        val imageUrl = MailImageUrlExtractor.findNearestCardImageUrl(curr, href)

                        result.add(
                            MailContent(
                                title = title,
                                content = desc,
                                link = href,
                                section = secName.ifBlank { "iOS Dev Weekly" },
                                imageUrl = imageUrl,
                            ),
                        )
                    }
                }
                curr = curr.nextElementSibling()
            }
        }

        // Fallback: If no role=heading tables matched, find direct article anchors
        if (result.isEmpty()) {
            val anchors = document.select("a[href]")
            for (anchor in anchors) {
                val href = anchor.attr("href").trim()
                val title = anchor.text().cleanInlineText()
                if (isValidArticle(title, href)) {
                    val p = anchor.nextElementSibling()?.takeIf { it.tagName() == "p" }
                    val desc = p?.text()?.cleanInlineText() ?: title
                    val imageUrl = MailImageUrlExtractor.findNearestCardImageUrl(anchor, href)
                    result.add(
                        MailContent(
                            title = title,
                            content = desc,
                            link = href,
                            section = "iOS Dev Weekly",
                            imageUrl = imageUrl,
                        ),
                    )
                }
            }
        }

        return result.distinctBy { it.link }
    }

    private fun isValidArticle(title: String, href: String): Boolean {
        if (title.length < 4 || href.isBlank()) return false
        if (href.startsWith("#")) return false
        val lowerTitle = title.lowercase()
        val lowerHref = href.lowercase()

        return EXCLUDED_KEYWORDS.none { lowerTitle.contains(it) || lowerHref.contains(it) }
    }

    private fun extractHtmlContent(content: String): String? {
        val htmlMarker = "Content-type: text/html"
        val startIndex = content.indexOf(htmlMarker, ignoreCase = true)
        if (startIndex != -1) {
            val bodyIndex = content.indexOf("\n\n", startIndex)
            if (bodyIndex != -1) {
                return content.substring(bodyIndex + 2).trim()
            }
        }
        return null
    }

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    companion object {
        private val EXCLUDED_SECTIONS = listOf("comment", "jobs", "job", "sponsor", "sponsors", "and finally")
        private val EXCLUDED_KEYWORDS = listOf(
            "unsubscribe", "view in browser", "read this issue on the web",
            "browse the archives", "privacy policy", "preferences", "curated by dave verwer",
            "ios dev weekly", "iosdevweekly.com/issues", "mailto:", "submit an article",
            "sponsor this newsletter", "last week’s survey"
        )
    }
}
