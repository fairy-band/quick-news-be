package com.nexters.newsletter.parser

import com.nexters.external.apiclient.CrawlerServiceClient

class MailParserFactory(
    private val crawlerServiceClient: CrawlerServiceClient? = null,
) {
    private val parsers =
        listOf(
            JSWeeklyParser(),
            LibHuntWeeklyParser(),
            KotlinWeeklyParser(),
            GeeknewsWeeklyParser(),
            MaeilMailParser(crawlerServiceClient),
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
