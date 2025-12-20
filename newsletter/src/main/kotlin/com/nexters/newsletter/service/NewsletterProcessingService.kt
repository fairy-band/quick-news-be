package com.nexters.newsletter.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.service.ContentAnalysisService
import com.nexters.external.service.ContentProviderService
import com.nexters.external.service.ContentService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.NewsletterSourceService
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
    private val contentProviderService: ContentProviderService,
    private val contentAnalysisService: ContentAnalysisService,
    private val exposureContentService: ExposureContentService,
) {
    private val logger = LoggerFactory.getLogger(NewsletterProcessingService::class.java)
    private val mailParserFactory = MailParserFactory()

    @Transactional
    fun processExistingContent(content: Content): ExposureContent {
        try {
            logger.info("Starting complete processing for existing content ID: $content.id")

            // Analyze content (summary + keywords in one call)
            analyzeContent(content)

            // Create exposure content
            val exposureContent = createExposureContent(content)

            logger.info("End complete processing for existing content ID: $content.id")
            return exposureContent
        } catch (e: Exception) {
            logger.error("Failed to process existing content ID: $content.id", e)
            throw e
        }
    }

    @Transactional
    fun processNewsletter(newsletterSourceId: String): List<ExposureContent> {
        try {
            val newsletterSource = newsletterSourceService.findById(newsletterSourceId)!!

            val parser =
                mailParserFactory.findParser(newsletterSource.senderEmail)
                    ?: return listOf() // 파서가 없는 경우 처리하지 않는다.

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

            logger.info("End complete newsletter processing for source ID: $newsletterSourceId")
            return createdContents.map { processExistingContent(it) }
        } catch (e: Exception) {
            logger.error("Failed to process newsletter source ID: $newsletterSourceId", e)
        }

        return listOf()
    }

    private fun parseContents(
        newsletterSource: NewsletterSource,
        parsedContents: List<MailContent>,
    ): List<Content> {
        logger.info("Creating ${parsedContents.size} contents from parsed data")

        val contentProvider = resolveContentProvider(newsletterSource.sender)

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
                    contentProvider = contentProvider,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                )

            val savedContent = contentService.save(content)
            logger.debug("Created content: ${savedContent.title} (ID: ${savedContent.id})")
            savedContent
        }
    }

    private fun resolveContentProvider(newsletterName: String): ContentProvider? =
        try {
            contentProviderService.findByName(newsletterName)
        } catch (e: Exception) {
            logger.warn("Failed to resolve content provider for: $newsletterName", e)
            null
        }

    private fun analyzeContent(content: Content) {
        logger.info("Analyzing content (summary + keywords): ${content.title}")

        // Analyze content and save both summary and keywords in one call
        contentAnalysisService.analyzeAndSave(content)
    }

    private fun createExposureContent(content: Content): ExposureContent {
        logger.info("Creating exposure content for content: ${content.title}")

        val summaries = contentAnalysisService.getPrioritizedSummaryByContent(content)

        return if (summaries.isNotEmpty()) {
            val latestSummary = summaries.first()
            exposureContentService.createExposureContentFromSummary(latestSummary.id!!)
        } else {
            // Fallback: create basic exposure content
            exposureContentService.createOrUpdateExposureContent(
                content = content,
                provocativeKeyword = "Newsletter",
                provocativeHeadline = content.title,
                summaryContent = content.content.take(500) + if (content.content.length > 500) "..." else "",
            )
        }
    }
}
