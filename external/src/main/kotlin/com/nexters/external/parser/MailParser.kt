package com.nexters.external.parser

interface MailParser {
    fun isTarget(sender: String): Boolean

    fun parse(content: String): List<MailContent>
}
