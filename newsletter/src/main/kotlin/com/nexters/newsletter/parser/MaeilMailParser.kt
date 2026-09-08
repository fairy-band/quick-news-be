package com.nexters.newsletter.parser

import com.nexters.external.apiclient.CrawlerServiceClient
import org.slf4j.LoggerFactory

class MaeilMailParser(
    private val crawlerServiceClient: CrawlerServiceClient? = null,
) : MailParser {
    private val logger = LoggerFactory.getLogger(MaeilMailParser::class.java)

    override fun supports(
        sender: String,
        subject: String?,
    ): Boolean = sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(context: MailParseContext): List<MailContent> {
        val existingItems = context.webPageEnrichment.successfulContentItems()
        if (existingItems.isNotEmpty()) {
            return existingItems.map { enrichmentItem -> enrichmentItem.toMailContent(context) }
        }

        val questionUrl = extractQuestionUrl(context) ?: run {
            logger.debug("No Maeil Mail question URL found in content or htmlContent")
            return emptyList()
        }

        if (crawlerServiceClient == null) {
            logger.debug("No CrawlerServiceClient configured for MaeilMailParser; skipping enrichment")
            return emptyList()
        }

        return try {
            logger.info("Extracting Maeil Mail content in real-time from crawler: $questionUrl")
            val extractResult = crawlerServiceClient.extractArticle(questionUrl)
            val extractedContent = extractResult?.content
            if (extractResult != null && extractResult.success && !extractedContent.isNullOrBlank()) {
                logger.info("Successfully extracted Maeil Mail question (${extractResult.length} chars) from $questionUrl")
                listOf(
                    MailContent(
                        title = context.subject.cleanTitle() ?: extractResult.title.cleanTitle() ?: "Untitled",
                        content = extractedContent.trim(),
                        link = questionUrl,
                        section = SECTION_INTERVIEW,
                        imageUrl = extractResult.imageUrl?.takeIf { it.isNotBlank() },
                        enrichmentKey = questionUrl,
                    ),
                )
            } else {
                logger.warn("Crawler extraction failed or empty for Maeil Mail ($questionUrl): ${extractResult?.error}")
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Exception during Maeil Mail crawler extraction for $questionUrl: ${e.message}")
            emptyList()
        }
    }

    private fun extractQuestionUrl(context: MailParseContext): String? {
        val body = context.htmlContent?.takeIf { it.isNotBlank() } ?: context.content
        return QUESTION_URL_REGEX.find(body)?.value
    }

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
        private val QUESTION_URL_REGEX = Regex("""https?://(?:www\.)?maeil-mail\.kr/question/\d+""")
    }
}
