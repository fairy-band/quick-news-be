package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.regex.Pattern

class MicroservicesIOParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains("chris@chrisrichardson.net", ignoreCase = true) ||
            sender.contains("microservices.io", ignoreCase = true) ||
            subject?.contains("Microservices.IO", ignoreCase = true) == true

    override fun parse(context: MailParseContext): List<MailContent> {
        val html = context.htmlContent?.takeIf { it.isNotBlank() }
            ?: extractHtmlContent(context.content)
            ?: context.content

        val document = Jsoup.parse(html)
        val subject = context.subject ?: ""

        val rawTitle = document.selectFirst("h1")?.text()?.cleanInlineText() ?: subject
        val title = cleanSubjectTitle(rawTitle)
        if (title.length < MIN_TITLE_LENGTH) {
            return emptyList()
        }

        val texts = mutableListOf<String>()
        for (node in document.select("p, div")) {
            if (node.children().none { it.tagName().lowercase() in listOf("p", "div", "ul", "ol") }) {
                val text = node.text().cleanInlineText()
                if (text.length > 30 && !isExcludedText(text)) {
                    texts.add(text)
                }
            }
        }

        val desc = texts.joinToString(" ").trim()
        if (desc.length < MIN_DESCRIPTION_LENGTH) {
            return emptyList()
        }

        val articleUrl = document.select("a[href]").find {
            val href = it.attr("href")
            (href.contains("microservices.io") || href.contains("chrisrichardson.net")) &&
                !href.contains("unsubscribe") &&
                !href.contains("consulting") &&
                !href.contains("training")
        }?.attr("href")?.cleanUrl() ?: "https://microservices.io"

        val imageUrl = MailImageUrlExtractor.findFirstContentImageUrl(html, articleUrl)

        return listOf(
            MailContent(
                title = title,
                content = desc.take(MAX_DESCRIPTION_LENGTH),
                link = articleUrl,
                section = "Microservices.IO",
                imageUrl = imageUrl,
            ),
        )
    }

    private fun cleanSubjectTitle(title: String): String {
        var t = title
        t = REMINDER_REGEX.matcher(t).replaceAll("")
        t = PREFIX_REGEX.matcher(t).replaceAll("")
        return t.cleanInlineText()
    }

    private fun isExcludedText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("unsubscribe") ||
            lower.contains("accelerating software delivery") ||
            lower.contains("need help with") ||
            lower.contains("you are receiving this email because") ||
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

    private fun String.cleanUrl(): String = replace(Regex("\\s+"), "").trim()

    companion object {
        private const val MIN_TITLE_LENGTH = 10
        private const val MIN_DESCRIPTION_LENGTH = 50
        private const val MAX_DESCRIPTION_LENGTH = 2_000

        private val REMINDER_REGEX = Pattern.compile("""^(?:Reminder:\s*)+""", Pattern.CASE_INSENSITIVE)
        private val PREFIX_REGEX = Pattern.compile("""^Microservices\.IO:\s*""", Pattern.CASE_INSENSITIVE)
    }
}
