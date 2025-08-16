package com.nexters.newsletter.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.entity.Summary
import com.nexters.external.service.ContentService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.KeywordService
import com.nexters.external.service.NewsletterSourceService
import com.nexters.external.service.SummaryService
import com.nexters.newsletter.parser.MailContent
import com.nexters.newsletter.parser.MailParserFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NewsletterProcessingService(
    private val newsletterSourceService: NewsletterSourceService,
    private val contentService: ContentService,
    private val summaryService: SummaryService,
    private val keywordService: KeywordService,
    private val exposureContentService: ExposureContentService
) {
    private val logger = LoggerFactory.getLogger(NewsletterProcessingService::class.java)
    private val mailParserFactory = MailParserFactory()

    @Transactional
    fun processNewsletter(newsletterSourceId: String): List<ExposureContent> {
        try {
            val newsletterSource = newsletterSourceService.findById(newsletterSourceId)!!

            val parser = mailParserFactory.findParser(newsletterSource.senderEmail)
            if (parser == null) {
                return listOf() // 파서가 없는 경우 처리하지 않는다.
            }

            logger.info("Starting complete newsletter processing for source ID: $newsletterSourceId")

            val parsedContents =
                try {
                    parser.parse(newsletterSource.content)
                } catch (e: Exception) {
                    logger.error("Failed to parse newsletter content", e)
                    throw IllegalStateException("Failed to parse newsletter content: ${e.message}")
                }

            if (parsedContents.isEmpty()) {
                throw IllegalStateException("No content was parsed from the newsletter")
            }

            val createdContents = parseContents(newsletterSource, parsedContents)

            generateSummaries(createdContents)

            matchKeywords(createdContents)

            val exposureContents = createExposureContents(createdContents)
            logger.info("End complete newsletter processing for source ID: $newsletterSourceId")
            return exposureContents
        } catch (e: Exception) {
            logger.error("Failed to process newsletter source ID: $newsletterSourceId", e)
        }

        return listOf()
    }

    private fun parseContents(
        newsletterSource: NewsletterSource,
        parsedContents: List<MailContent>
    ): List<Content> {
        logger.info("Creating ${parsedContents.size} contents from parsed data")

        return parsedContents.map { mailContent ->
            // Extract original URL from RSS header if available, otherwise use parsed link
            val originalUrl = newsletterSource.headers["RSS-Item-URL"] ?: mailContent.link

            val content =
                Content(
                    newsletterSourceId = newsletterSource.id,
                    title = mailContent.title,
                    content = mailContent.content,
                    newsletterName = newsletterSource.sender,
                    originalUrl = originalUrl,
                    publishedAt = newsletterSource.receivedDate.toLocalDate(),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

            val savedContent = contentService.save(content)
            logger.debug("Created content: ${savedContent.title} (ID: ${savedContent.id})")
            savedContent
        }
    }

    private fun generateSummaries(contents: List<Content>) {
        logger.info("Generating summaries for ${contents.size} contents")

        contents.forEach { content ->
            try {
                val summaryResult = summaryService.summarize(content.content)

                // Use first provocative headline if available, otherwise use original title
                val recommendedTitle = summaryResult.provocativeHeadlines.firstOrNull() ?: content.title

                val summary =
                    Summary(
                        content = content,
                        title = recommendedTitle,
                        summarizedContent = summaryResult.summary,
                        model = summaryResult.usedModel?.modelName ?: "unknown",
                        summarizedAt = LocalDateTime.now(),
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )

                summaryService.save(summary)
            } catch (e: Exception) {
                logger.error("Failed to create summary for content: ${content.title}", e)
            }
        }
    }

    private fun matchKeywords(contents: List<Content>) {
        logger.info("Matching keywords for ${contents.size} contents")

        contents.forEach { content ->
            try {
                val matchedKeywords = keywordService.matchReservedKeywords(content.content)

                // 컨텐츠에 키워드 할당
                val assignedCount = keywordService.assignKeywordsToContent(content, matchedKeywords)

                logger.debug("Matched ${matchedKeywords.size} keywords for content: ${content.title}, assigned $assignedCount new keywords")
            } catch (e: Exception) {
                logger.error("Failed to match keywords for content: ${content.title}", e)
                // Continue with other contents even if one fails
            }
        }
    }

    private fun createExposureContents(contents: List<Content>): List<ExposureContent> {
        logger.info("Creating exposure contents for ${contents.size} contents")

        return contents.map { content ->
            val summaries = summaryService.getPrioritizedSummaryByContent(content)

            if (summaries.isNotEmpty()) {
                val latestSummary = summaries.first()
                exposureContentService.createExposureContentFromSummary(latestSummary.id!!)
            } else {
                // Fallback: create basic exposure content
                exposureContentService.createOrUpdateExposureContent(
                    content = content,
                    provocativeKeyword = "Newsletter",
                    provocativeHeadline = content.title,
                    summaryContent = content.content.take(500) + if (content.content.length > 500) "..." else ""
                )
            }
        }
    }
}
