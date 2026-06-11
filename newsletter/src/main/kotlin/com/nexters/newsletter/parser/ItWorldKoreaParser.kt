package com.nexters.newsletter.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ItWorldKoreaParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun isProcessable(
        sender: String,
        subject: String?,
    ): Boolean = isTarget(sender) && subject?.contains(NEWSLETTER_SUBJECT_MARKER) == true

    override fun parse(content: String): List<MailContent> = parse(content, null, null)

    override fun parse(
        content: String,
        subject: String?,
        htmlContent: String?,
    ): List<MailContent> {
        if (subject?.contains(NEWSLETTER_SUBJECT_MARKER) != true) return emptyList()

        val document = Jsoup.parse(selectHtmlSource(content, htmlContent))
        val linkByLabel = document.select("a[linklabel]").associateBy { element -> element.attr("linklabel") }
        val titleElements =
            (document.select("a[linklabel$=Title]") + document.select("h2.article-title a[href]"))
                .distinctBy { element -> element.text().cleanInlineText() to element.attr("href") }

        return titleElements
            .asSequence()
            .mapNotNull { titleElement ->
                val descriptionElement =
                    findDescriptionElement(
                        titleElement = titleElement,
                        linkByLabel = linkByLabel,
                    )
                titleElement.toMailContent(descriptionElement)
            }.distinctBy { it.title.lowercase() }
            .take(MAX_ARTICLE_COUNT)
            .toList()
    }

    private fun selectHtmlSource(
        content: String,
        htmlContent: String?,
    ): String {
        val html = htmlContent.orEmpty()
        return if (html.hasArticleMarkers()) html else content
    }

    private fun findDescriptionElement(
        titleElement: Element,
        linkByLabel: Map<String, Element>,
    ): Element? {
        val label = titleElement.attr("linklabel")
        if (label.endsWith("Title")) {
            linkByLabel[label.replace("Title", "Description")]?.let { return it }
        }

        return titleElement
            .parents()
            .firstOrNull { parent -> parent.hasClass("description") }
            ?.select("a[linklabel*=Description]")
            ?.firstOrNull()
    }

    private fun Element.toMailContent(descriptionElement: Element?): MailContent? {
        val title = text().cleanInlineText()
        val description = descriptionElement?.text()?.cleanInlineText().orEmpty()
        val link = attr("href").extractOriginalUrl()

        if (title.length < MIN_TITLE_LENGTH || description.length < MIN_DESCRIPTION_LENGTH || link.isBlank()) {
            return null
        }

        if (EXCLUDED_TITLE_PARTS.any { title.contains(it, ignoreCase = true) }) {
            return null
        }

        return MailContent(
            title = title,
            content = description,
            link = link,
            section = SECTION_NEWS,
            imageUrl = findLinkedCardImageUrl(link) ?: MailImageUrlExtractor.findNearestCardImageUrl(this, link),
        )
    }

    private fun Element.findLinkedCardImageUrl(link: String): String? =
        ownerDocument()
            ?.select("a[href]")
            ?.asSequence()
            ?.filter { anchor -> anchor.attr("href").extractOriginalUrl() == link }
            ?.mapNotNull { anchor -> MailImageUrlExtractor.findNearestCardImageUrl(anchor, link, maxAncestorDepth = 0) }
            ?.firstOrNull()

    private fun String.extractOriginalUrl(): String {
        val query = substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return cleanUrl()

        val encodedUrl =
            query
                .split("&")
                .firstOrNull { part -> part.startsWith("url=") }
                ?.substringAfter("=")

        return encodedUrl
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?.cleanUrl()
            ?: cleanUrl()
    }

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.hasArticleMarkers(): Boolean =
        contains("linklabel=", ignoreCase = true) ||
            contains("article-title", ignoreCase = true)

    private fun String.cleanUrl(): String = trim()

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "itworld@techlibrary.co.kr"
        private const val NEWSLETTER_SUBJECT_MARKER = "[ITWorld 뉴스레터]"
        private const val SECTION_NEWS = "ITWorld News"

        private val EXCLUDED_TITLE_PARTS =
            listOf(
                "다운로드",
                "테크라이브러리",
            )

        private const val MIN_TITLE_LENGTH = 8
        private const val MIN_DESCRIPTION_LENGTH = 20
        private const val MAX_ARTICLE_COUNT = 12
    }
}
