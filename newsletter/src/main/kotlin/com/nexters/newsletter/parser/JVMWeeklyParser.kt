package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64
import java.util.regex.Pattern

class JVMWeeklyParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean =
        sender.contains("vived@substack.com", ignoreCase = true) ||
            sender.contains("JVM Weekly", ignoreCase = true) ||
            subject?.contains("JVM Weekly", ignoreCase = true) == true

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
        val canonicalLink = findCanonicalLink(document)
        val seenUrls = mutableSetOf<String>()

        val headings = document.select("h2, h3")
        for (h in headings) {
            val title = h.text().cleanInlineText()
            if (!isValidArticleTitle(title)) {
                continue
            }

            var link: String? = h.selectFirst("a[href]")?.attr("href")?.trim()

            val descParts = mutableListOf<String>()
            var curr: Element? = h.nextElementSibling()
            while (curr != null && descParts.size < 4) {
                val tag = curr.tagName().lowercase()
                if (tag in listOf("h1", "h2", "h3", "hr")) {
                    break
                }
                if (tag in listOf("p", "div", "blockquote", "ul", "ol")) {
                    val text = curr.text().cleanInlineText()
                    if (text.isNotBlank()) {
                        descParts.add(text)
                        if (link.isNullOrBlank()) {
                            val candidateLink = curr.selectFirst("a[href]")?.attr("href")?.trim()
                            if (!candidateLink.isNullOrBlank() && isUsefulExternalLink(candidateLink)) {
                                link = candidateLink
                            }
                        }
                    }
                }
                curr = curr.nextElementSibling()
            }

            val desc = descParts.joinToString(" ").trim()
            if (desc.length < MIN_DESCRIPTION_LENGTH) {
                continue
            }

            val finalUrl = resolveRedirect(link ?: canonicalLink ?: "https://jvm-weekly.com")
            val normalizedUrl = finalUrl.substringBefore("#").lowercase()
            if (!seenUrls.add(normalizedUrl)) {
                continue
            }
            val imageUrl = MailImageUrlExtractor.findNearestCardImageUrl(h, finalUrl)

            results.add(
                MailContent(
                    title = title,
                    content = desc.take(MAX_DESCRIPTION_LENGTH),
                    link = finalUrl,
                    section = "JVM Weekly",
                    imageUrl = imageUrl,
                ),
            )
        }

        return results
    }

    private fun findCanonicalLink(document: Document): String? {
        val link = document.select("a[href*=\"open.substack.com/pub/vived/p/\"]").firstOrNull()?.attr("href")
        return link?.substringBefore("?")
    }

    private fun isUsefulExternalLink(href: String): Boolean {
        if (!href.startsWith("http")) return false
        val lower = href.lowercase()
        return !lower.contains("substack.com/app-link") &&
            !lower.contains("substack.com/profile") &&
            !lower.contains("substack.com/account") &&
            !lower.contains("mailto:")
    }

    private fun isValidArticleTitle(title: String): Boolean {
        if (title.length < 4) return false
        val lower = title.lowercase()
        if (EXCLUDED_TITLE_PREFIXES.any { lower.startsWith(it) || lower == it }) {
            return false
        }
        if (NUMBERED_SECTION_REGEX.matcher(title).matches()) {
            return false
        }
        return true
    }

    private fun resolveRedirect(url: String): String {
        try {
            if (url.contains("/redirect/2/")) {
                val token = url.substringAfter("/redirect/2/").substringBefore("?")
                val padded = token + "=".repeat((4 - token.length % 4) % 4)
                val decoded = String(Base64.getUrlDecoder().decode(padded))
                val matcher = REDIRECT_TARGET_REGEX.matcher(decoded)
                if (matcher.find()) {
                    val dest = matcher.group(1)
                    if (dest.startsWith("http")) {
                        return dest
                    }
                }
            }
        } catch (_: Exception) {
            // fallback to original url
        }
        return url
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
        private const val MIN_DESCRIPTION_LENGTH = 30
        private const val MAX_DESCRIPTION_LENGTH = 1_500

        private val NUMBERED_SECTION_REGEX = Pattern.compile("""^\d+\.\s+[A-Za-z]+.*""")
        private val REDIRECT_TARGET_REGEX = Pattern.compile(""""e"\s*:\s*"(https?:[^"]+)"""")

        private val EXCLUDED_TITLE_PREFIXES = listOf(
            "subscribe",
            "read in app",
            "share",
            "sponsor",
            "missed in",
            "release radar",
            "github all-stars",
            "community update",
            "upcoming events",
            "rest of the story",
            "the rest of the story",
            "jvm weekly",
            "volume",
            "issue",
            "advertisement",
        )
    }
}
