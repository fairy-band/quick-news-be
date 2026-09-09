package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import java.util.regex.Pattern

class SwiftUIWeeklyParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains("swiftuiweekly@substack.com", ignoreCase = true) ||
            sender.contains("swiftui weekly", ignoreCase = true) ||
            subject?.contains("SwiftUI Weekly", ignoreCase = true) == true

    override fun parse(context: MailParseContext): List<MailContent> {
        val content = context.content
        val htmlContent = context.htmlContent

        val plainTextArticles = parsePlainText(content)
        if (plainTextArticles.isNotEmpty()) {
            return plainTextArticles
        }

        if (!htmlContent.isNullOrBlank()) {
            return parseHtml(htmlContent)
        }

        return emptyList()
    }

    private fun parsePlainText(content: String): List<MailContent> {
        val matches = SUBSTACK_ITEM_PATTERN.matcher(content)
        val items = mutableListOf<Pair<String, String>>()
        val startPositions = mutableListOf<Int>()
        val endPositions = mutableListOf<Int>()

        while (matches.find()) {
            val title = matches.group(1).cleanInlineText()
            val url = matches.group(2).trim()
            if (isValidArticle(title, url)) {
                items.add(title to url)
                startPositions.add(matches.start())
                endPositions.add(matches.end())
            }
        }

        if (items.isEmpty()) return emptyList()

        val results = mutableListOf<MailContent>()
        for (i in items.indices) {
            val (title, url) = items[i]
            val descStart = endPositions[i]
            val descEnd = if (i + 1 < startPositions.size) startPositions[i + 1] else (descStart + 500).coerceAtMost(content.length)
            val rawDesc = content.substring(descStart, descEnd).cleanInlineText()
            val desc = if (rawDesc.length > 20) rawDesc else title

            results.add(
                MailContent(
                    title = title,
                    content = desc,
                    link = url,
                    section = "SwiftUI Weekly",
                ),
            )
        }

        return results.distinctBy { it.link }
    }

    private fun parseHtml(html: String): List<MailContent> {
        val document = Jsoup.parse(html)
        val results = mutableListOf<MailContent>()
        val anchors = document.select("a[href]")

        for (a in anchors) {
            val title = a.text().cleanInlineText()
            val href = a.attr("href").trim()
            if (isValidArticle(title, href)) {
                val p = a.parents().firstOrNull { it.tagName() in listOf("p", "li", "div") }
                val desc = p?.text()?.cleanInlineText() ?: title
                results.add(
                    MailContent(
                        title = title,
                        content = desc,
                        link = href,
                        section = "SwiftUI Weekly",
                    ),
                )
            }
        }
        return results.distinctBy { it.link }
    }

    private fun isValidArticle(title: String, href: String): Boolean {
        if (title.length < 5 || href.isBlank()) return false
        if (href.startsWith("#")) return false
        val lowerTitle = title.lowercase()
        val lowerHref = href.lowercase()

        return EXCLUDED_KEYWORDS.none { lowerTitle.contains(it) || lowerHref.contains(it) }
    }

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    companion object {
        private val SUBSTACK_ITEM_PATTERN =
            Pattern.compile("""^([A-Z0-9][^\[\n]+?)\s*\[\s*(https?://[^\s\]]+)\s*\]""", Pattern.MULTILINE)
        private val EXCLUDED_KEYWORDS = listOf(
            "view this post on the web", "read in app", "share", "unsubscribe",
            "restack", "privacy policy", "substack.com/app-link", "issue #"
        )
    }
}
