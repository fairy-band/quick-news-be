package com.nexters.newsletterfeeder.parser

interface MailParser {
    fun isTarget(sender: String): Boolean

    fun parse(content: String): List<MailContent>
}
