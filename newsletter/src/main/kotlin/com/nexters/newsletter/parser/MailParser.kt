package com.nexters.newsletter.parser

sealed interface MailParser {
    fun supports(
        sender: String,
        subject: String?,
    ): Boolean

    fun parse(context: MailParseContext): List<MailContent>
}

fun MailParser.parse(
    content: String,
    subject: String? = null,
    htmlContent: String? = null,
): List<MailContent> =
    parse(
        MailParseContext(
            content = content,
            subject = subject,
            htmlContent = htmlContent,
        ),
    )
