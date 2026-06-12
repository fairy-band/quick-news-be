package com.nexters.newsletter.parser

class MailParserFactory {
    private val parsers =
        listOf(
            JSWeeklyParser(),
            JavaWeeklyParser(),
            KotlinWeeklyParser(),
            GeeknewsWeeklyParser(),
            MaeilMailParser(),
            KoreanFeArticleParser(),
            TLDRNewsletterParser(),
            BaeldungParser(),
            YozmParser(),
            BytesDevParser(),
            WebToolsWeeklyParser(),
            VSCodeEmailParser(),
            GenericSubstackArticleParser(),
            ReactStatusParser(),
            CooperpressWeeklyParser(),
            PythonWeeklyParser(),
            AndroidWeeklyParser(),
            ItWorldKoreaParser(),
            CssWeeklyParser(),
            SwiftVincentParser(),
            IlbunParser(),
        )

    fun findParser(
        sender: String,
        subject: String?,
    ): MailParser? = parsers.find { it.supports(sender, subject) }

    fun getAllParsers(): List<MailParser> = parsers
}
