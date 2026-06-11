package com.nexters.newsletter.parser

import org.jsoup.Jsoup

class GenericSubstackArticleParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        PUBLICATIONS.any { publication ->
            publication.targetSenders.any { target -> sender.contains(target, ignoreCase = true) }
        }

    override fun parse(content: String): List<MailContent> = parse(content, null, null)

    override fun parse(
        content: String,
        subject: String?,
        htmlContent: String?,
    ): List<MailContent> {
        val normalized = content.normalizeNewsletterText()
        val title = subject.cleanTitle() ?: extractTitleFromHtml(htmlContent) ?: extractFallbackTitle(normalized) ?: return emptyList()
        val link = extractArticleLink(normalized, htmlContent)
        val publication = detectPublication(link, normalized, htmlContent)
        val description = extractDescription(normalized).ifBlank { title }
        val imageUrl = MailImageUrlExtractor.findFirstContentImageUrl(htmlContent, link)

        return listOf(
            MailContent(
                title = title,
                content = description,
                link = link.takeIf { it.isNotBlank() } ?: publication.defaultLink,
                section = publication.name,
                imageUrl = imageUrl,
            ),
        )
    }

    private data class Publication(
        val name: String,
        val targetSenders: List<String>,
        val linkHints: List<String>,
        val contentHints: List<String> = emptyList(),
        val defaultLink: String,
    )

    private fun extractArticleLink(
        content: String,
        htmlContent: String?,
    ): String {
        val viewPostLink =
            VIEW_POST_REGEX
                .find(content)
                ?.groupValues
                ?.getOrNull(1)
                ?.cleanUrl()
        if (!viewPostLink.isNullOrBlank()) return viewPostLink

        val html = htmlContent.orEmpty()
        val postLink =
            HTML_POST_LINK_REGEX
                .findAll(html)
                .map { match -> match.groupValues[1].cleanUrl() }
                .firstOrNull { url -> PUBLICATIONS.any { publication -> publication.matchesLink(url) } }

        if (!postLink.isNullOrBlank()) return postLink

        return detectPublication("", content, htmlContent).defaultLink
    }

    private fun detectPublication(
        link: String,
        content: String,
        htmlContent: String?,
    ): Publication {
        val haystack = "$link\n$content\n${htmlContent.orEmpty()}".lowercase()
        return PUBLICATIONS.firstOrNull { publication ->
            publication.linkHints.any { hint -> haystack.contains(hint.lowercase()) } ||
                publication.contentHints.any { hint -> haystack.contains(hint.lowercase()) }
        } ?: DEFAULT_PUBLICATION
    }

    private fun Publication.matchesLink(link: String): Boolean = linkHints.any { hint -> link.contains(hint, ignoreCase = true) }

    private fun extractDescription(content: String): String =
        content
            .removeViewPostLine()
            .removeBlock(STREAM_EPISODE_MARKER, BROUGHT_TO_YOU_MARKER)
            .removeBlock(BROUGHT_TO_YOU_MARKER, CONTENT_START_MARKERS)
            .substringBefore(UNSUBSCRIBE_MARKER)
            .replace(SUBSTACK_INLINE_LINK_REGEX, "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line -> EXCLUDED_LINES.any { excluded -> line.equals(excluded, ignoreCase = true) } }
            .joinToString(" ")
            .cleanInlineText()
            .take(MAX_DESCRIPTION_LENGTH)

    private fun String.removeViewPostLine(): String = replace(VIEW_POST_LINE_REGEX, "")

    private fun String.removeBlock(
        startMarker: String,
        endMarker: String,
    ): String {
        val start = indexOf(startMarker, ignoreCase = true)
        if (start < 0) return this

        val end = indexOf(endMarker, start + startMarker.length, ignoreCase = true)
        if (end < 0) return this

        return removeRange(start, end)
    }

    private fun String.removeBlock(
        startMarker: String,
        endMarkers: List<String>,
    ): String {
        val start = indexOf(startMarker, ignoreCase = true)
        if (start < 0) return this

        val end =
            endMarkers
                .map { marker -> indexOf(marker, start + startMarker.length, ignoreCase = true) }
                .filter { index -> index >= 0 }
                .minOrNull() ?: return this

        return removeRange(start, end)
    }

    private fun extractTitleFromHtml(htmlContent: String?): String? {
        val title =
            htmlContent
                ?.takeIf { it.isNotBlank() }
                ?.let { html ->
                    Jsoup
                        .parse(html)
                        .selectFirst("h1.post-title, h1")
                        ?.text()
                        ?.cleanInlineText()
                }

        return title?.takeIf { it.length >= MIN_TITLE_LENGTH }
    }

    private fun extractFallbackTitle(content: String): String? =
        content
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.startsWith("View this post", ignoreCase = true) &&
                    !line.startsWith("Unsubscribe", ignoreCase = true)
            }?.cleanInlineText()
            ?.takeIf { it.length >= MIN_TITLE_LENGTH }

    private fun String?.cleanTitle(): String? =
        this
            ?.cleanInlineText()
            ?.takeIf { it.length >= MIN_TITLE_LENGTH }

    private fun String.normalizeNewsletterText(): String =
        replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u00A0", " ")
            .replace("\u200B", "")

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    private fun String.cleanUrl(): String = trim().trimEnd(')', ']', ',', '.')

    companion object {
        private const val STREAM_EPISODE_MARKER = "Stream the latest episode"
        private const val BROUGHT_TO_YOU_MARKER = "Brought to You by"
        private const val UNSUBSCRIBE_MARKER = "\nUnsubscribe"
        private const val MIN_TITLE_LENGTH = 5
        private const val MAX_DESCRIPTION_LENGTH = 3_000

        private val VIEW_POST_REGEX = Regex("""View this post on the web at\s+(https?://\S+)""", RegexOption.IGNORE_CASE)
        private val VIEW_POST_LINE_REGEX = Regex("""(?im)^View this post on the web at\s+https?://\S+\s*""")
        private val HTML_POST_LINK_REGEX = Regex("""href=["'](https?://[^"']+/p/[^"']+)["']""")
        private val SUBSTACK_INLINE_LINK_REGEX = Regex("""\s*\[\s*https?://[^]]+]""")
        private val CONTENT_START_MARKERS =
            listOf(
                "In this episode",
                "Summary",
                "Many people",
                "How soon",
                "Welcome to",
            )
        private val EXCLUDED_LINES =
            listOf(
                "Subscribe",
                "Share",
            )

        private val DEFAULT_PUBLICATION =
            Publication(
                name = "Substack Article",
                targetSenders = emptyList(),
                linkHints = emptyList(),
                defaultLink = "https://substack.com",
            )

        private val PUBLICATIONS =
            listOf(
                Publication(
                    name = "The Pragmatic Engineer",
                    targetSenders =
                        listOf(
                            "pragmaticengineer@substack.com",
                            "pragmaticengineer+deepdives@substack.com",
                        ),
                    linkHints =
                        listOf(
                            "newsletter.pragmaticengineer.com",
                            "pub/pragmaticengineer",
                        ),
                    defaultLink = "https://newsletter.pragmaticengineer.com",
                ),
                Publication(
                    name = "The Practical Stack",
                    targetSenders = listOf("thepracticalstack461@substack.com"),
                    linkHints =
                        listOf(
                            "thepracticalstack461.substack.com",
                            "pub/thepracticalstack461",
                        ),
                    defaultLink = "https://thepracticalstack461.substack.com",
                ),
                Publication(
                    name = "The Coder Cafe",
                    targetSenders = listOf("thecodercafe+concepts@substack.com"),
                    linkHints =
                        listOf(
                            "read.thecoder.cafe",
                            "pub/thecodercafe",
                        ),
                    defaultLink = "https://read.thecoder.cafe",
                ),
                Publication(
                    name = "Architecture Weekly",
                    targetSenders = listOf("architectureweekly@substack.com"),
                    linkHints =
                        listOf(
                            "architecture-weekly.com",
                            "pub/architectureweekly",
                        ),
                    contentHints =
                        listOf(
                            "@oskardudycz",
                            "Oskar Dudycz",
                        ),
                    defaultLink = "https://www.architecture-weekly.com",
                ),
                Publication(
                    name = "Fatbobman's Swift Weekly",
                    targetSenders = listOf("fatbobman@substack.com"),
                    linkHints =
                        listOf(
                            "weekly.fatbobman.com",
                            "fatbobman.com",
                            "pub/fatbobman",
                        ),
                    contentHints =
                        listOf(
                            "Fatbobman",
                        ),
                    defaultLink = "https://weekly.fatbobman.com",
                ),
                Publication(
                    name = "Jacob's Tech Tavern",
                    targetSenders = listOf("jacobbartlett@substack.com"),
                    linkHints =
                        listOf(
                            "blog.jacobstechtavern.com",
                            "jacobbartlett.substack.com",
                            "pub/jacobbartlett",
                        ),
                    contentHints =
                        listOf(
                            "Jacob's Tech Tavern",
                            "Jacob Bartlett",
                        ),
                    defaultLink = "https://blog.jacobstechtavern.com",
                ),
            )
    }
}
