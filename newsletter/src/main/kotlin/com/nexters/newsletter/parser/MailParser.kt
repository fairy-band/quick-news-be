package com.nexters.newsletter.parser

sealed interface MailParser {
    fun isTarget(sender: String): Boolean

    fun parse(content: String): List<MailContent>
}
