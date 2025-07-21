package com.nexters.newsletterfeeder.parser

object MailParserFactory {
    private val parsers = listOf(
        ReactStatusParser(),
        KotlinWeeklyParser(),
        // 다른 파서들 추가
    )

    fun from(sender: String): MailParser? = parsers.firstOrNull { it.isTarget(sender) }
}
