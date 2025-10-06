package com.nexters.newsletter.parser

class MailParserFactory {
    private val parsers =
        listOf(
            KotlinWeeklyParser(),
            AndroidWeeklyParser(),
            GeeknewsWeeklyParser()
        )

    fun findParser(sender: String): MailParser? = parsers.find { it.isTarget(sender) }

    fun getAllParsers(): List<MailParser> = parsers
}
