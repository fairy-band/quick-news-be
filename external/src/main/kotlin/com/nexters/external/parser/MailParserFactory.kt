package com.nexters.external.parser

class MailParserFactory {
    private val parsers =
        listOf(
            KotlinWeeklyParser(),
            CssWeeklyParser(),
            SwiftVincentParser(),
            ReactStatusParser(),
            IlbunParser()
        )

    fun findParser(sender: String): MailParser? = parsers.find { it.isTarget(sender) }

    fun getAllParsers(): List<MailParser> = parsers
}
