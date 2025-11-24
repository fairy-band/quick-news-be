package com.nexters.newsletter.parser

class MailParserFactory {
    private val parsers =
        listOf(
            JSWeeklyParser(),
            KotlinWeeklyParser(),
            GeeknewsWeeklyParser(),
            JavaWeeklyParser(),
        )

    fun findParser(sender: String): MailParser? = parsers.find { it.isTarget(sender) }

    fun getAllParsers(): List<MailParser> = parsers
}
