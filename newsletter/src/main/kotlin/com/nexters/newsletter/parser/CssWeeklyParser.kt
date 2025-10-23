package com.nexters.newsletter.parser

class CssWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val lines = content.lines()
        return parseSections(lines)
    }

    private fun parseSections(lines: List<String>): List<MailContent> {
        val sectionPositions = findSectionPositions(lines)
        val articlePositions = findArticlePositions(lines)

        return articlePositions.map { (index, articleData) ->
            val section = findCurrentSection(index, sectionPositions)
            val content = extractArticleContent(lines, index)

            MailContent(
                title = articleData.title.removeSuffix("✨").trim(),
                content = content,
                link = articleData.link,
                section = section
            )
        }
    }

    private fun findSectionPositions(lines: List<String>): List<IndexedValue<String>> =
        lines
            .withIndex()
            .filter { (_, line) -> SECTION_HEADER_REGEX.matches(line.trim()) }
            .map { (index, line) ->
                IndexedValue(
                    index,
                    SECTION_HEADER_REGEX
                        .find(line.trim())
                        ?.groupValues
                        ?.get(1)
                        ?.trim() ?: ""
                )
            }

    private fun findArticlePositions(lines: List<String>): List<IndexedValue<ArticleData>> =
        lines
            .withIndex()
            .mapNotNull { (index, line) ->
                parseArticleLine(line.trim())?.let { articleData ->
                    IndexedValue(index, articleData)
                }
            }

    private fun parseArticleLine(line: String): ArticleData? {
        val match = ARTICLE_PATTERN_REGEX.find(line) ?: return null
        val title = match.groupValues[1]
        val link = match.groupValues[2]

        return ArticleData(title, link)
    }

    private fun findCurrentSection(
        articleIndex: Int,
        sections: List<IndexedValue<String>>
    ): String? =
        sections
            .filter { it.index < articleIndex }
            .maxByOrNull { it.index }
            ?.value

    private fun extractArticleContent(
        lines: List<String>,
        startIndex: Int
    ): String =
        lines
            .drop(startIndex + 1)
            .takeWhile { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("# ") && !trimmed.startsWith("## ")
            }.joinToString("\n")
            .trim()

    private data class ArticleData(
        val title: String,
        val link: String
    )

    companion object {
        private val SECTION_HEADER_REGEX = Regex("""^# (.+)$""")
        private val ARTICLE_PATTERN_REGEX = Regex("""^## \[(.*?)\]\((https?://[^)]+)\).*$""")

        private const val NEWSLETTER_NAME = "CSS Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "css-weekly@beehiiv.com"
    }
}
