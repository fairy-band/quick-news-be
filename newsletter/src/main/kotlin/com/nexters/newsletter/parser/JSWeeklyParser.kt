package com.nexters.newsletter.parser

class JSWeeklyParser: MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        // Do Nothing Now
        TODO()
    }

    companion object {
        private const val NEWSLETTER_NAME = "Javascript Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "jsw@peterc.org"
    }
}
