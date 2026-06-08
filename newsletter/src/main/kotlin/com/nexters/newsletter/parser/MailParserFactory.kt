package com.nexters.newsletter.parser

class MailParserFactory {
    private val parsers =
        listOf(
            JSWeeklyParser(),
            JavaWeeklyParser(),
            KotlinWeeklyParser(),
            GeeknewsWeeklyParser(),
            KoreanFeArticleParser(),
            BytesDevParser(),
            WebToolsWeeklyParser(),
            VSCodeEmailParser(),
            GenericSubstackArticleParser(),
            CooperpressWeeklyParser(),
            PythonWeeklyParser(),
            AndroidWeeklyParser(),
            ItWorldKoreaParser(),
            CssWeeklyParser(),
            SwiftVincentParser(),
            IlbunParser(),
            ReactStatusParser(),
        )

    fun findParser(sender: String): MailParser? = parsers.find { it.isTarget(sender) }

    fun getAllParsers(): List<MailParser> = parsers
}
