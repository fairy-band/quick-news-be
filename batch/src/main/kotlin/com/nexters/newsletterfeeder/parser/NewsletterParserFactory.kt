package com.nexters.newsletterfeeder.parser

object MailParserFactory {
    private val parsers = listOf(
        ReactStatusParser(),
        KotlinWeeklyParser(),
        // 다른 파서들 추가
    )

    fun getParser(sender: String): MailParser? {
        return parsers.firstOrNull { it.isTarget(sender) }
    }
}

