package com.nexters.newsletter.parser

class MaeilMailParser : MailParser {
    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(context: MailParseContext): List<MailContent> =
        context.webPageEnrichment
            .successfulContentItems()
            .map { enrichmentItem -> enrichmentItem.toMailContent(context) }

    private fun MailWebPageEnrichmentItem.toMailContent(context: MailParseContext): MailContent =
        MailContent(
            title = titleFromEnrichment(context),
            content = content!!.trim(),
            link = url.trim(),
            section = SECTION_INTERVIEW,
            imageUrl = imageUrl?.takeIf { imageUrl -> imageUrl.isNotBlank() },
            enrichmentKey = enrichmentKey,
        )

    private fun MailWebPageEnrichmentItem.titleFromEnrichment(context: MailParseContext): String {
        val titleCandidates =
            sequenceOf(
                title,
                context.subject,
                url,
            )

        return titleCandidates.mapNotNull { candidate -> candidate.cleanTitle() }.firstOrNull() ?: "Untitled"
    }

    private fun String?.cleanTitle(): String? {
        val cleaned =
            this
                ?.replace(SUBJECT_PREFIX_REGEX, "")
                ?.replace(TITLE_WHITESPACE_REGEX, " ")
                ?.trim()

        return cleaned?.takeIf { title -> title.isNotBlank() }
    }

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "noreply@maeil-mail.kr"
        private const val SECTION_INTERVIEW = "Maeil Mail"

        private val SUBJECT_PREFIX_REGEX = Regex("""^\s*\[매일메일]\s*""")
        private val TITLE_WHITESPACE_REGEX = Regex("\\s+")
    }
}
