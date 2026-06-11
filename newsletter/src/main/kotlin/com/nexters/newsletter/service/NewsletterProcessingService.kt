package com.nexters.newsletter.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.service.ContentAnalysisService
import com.nexters.external.service.ContentProviderService
import com.nexters.external.service.ContentService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.NewsletterSourceService
import com.nexters.external.service.RepresentativeImageUrlExtractorService
import com.nexters.newsletter.parser.MailContent
import com.nexters.newsletter.parser.MailParserFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
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
    private val representativeImageUrlExtractorService: RepresentativeImageUrlExtractorService,
    private val newsletterContentGroupingServiceProvider: ObjectProvider<NewsletterContentGroupingService>,
) {
    private val logger = LoggerFactory.getLogger(NewsletterProcessingService::class.java)
    private val mailParserFactory = MailParserFactory()

    @Transactional
    fun processExistingContent(content: Content): ExposureContent {
        try {
            logger.info("Starting complete processing for existing content ID: ${content.id}")

            // 콘텐츠 길이 검증
            val contentLength = content.content.length
            if (contentLength > MAX_CONTENT_LENGTH) {
                val errorMsg = "Content ID ${content.id} exceeds max length ($contentLength > $MAX_CONTENT_LENGTH). Skipping AI processing."
                logger.warn(errorMsg)
                throw IllegalArgumentException(errorMsg)
            }

            // Analyze content (summary + keywords in one call)
            // Note: Rate Limiter는 ContentAnalysisService#analyzeContent 내부에서 자동으로 적용됨
            analyzeContent(content)

            // Create exposure content
            val exposureContent = createExposureContent(content)

            logger.info("End complete processing for existing content ID: ${content.id}")
            return exposureContent
        } catch (e: RateLimitExceededException) {
            logger.error(
                "Rate limit exceeded for content ID: ${content.id}. " +
                    "LimitType: ${e.limitType}, Model: ${e.modelName}",
                e
            )
            throw e
        } catch (e: IllegalArgumentException) {
            // 콘텐츠 길이 초과 등의 검증 오류
            logger.error("Validation failed for content ID: ${content.id}", e)
            throw e
        } catch (e: Exception) {
            logger.error("Failed to process existing content ID: ${content.id}", e)
            throw e
        }
    }

    @Transactional
    fun processNewsletter(newsletterSourceId: String): List<ExposureContent> {
        try {
            val newsletterSource = newsletterSourceService.findById(newsletterSourceId)!!

            val parser =
                mailParserFactory.findProcessableParser(newsletterSource.senderEmail, newsletterSource.subject)
                    ?: return listOf() // 파서가 없는 경우 처리하지 않는다.

            logger.info("Starting complete newsletter processing for source ID: $newsletterSourceId")

            val parsedContents =
                try {
                    parser.parse(
                        content = newsletterSource.content,
                        subject = newsletterSource.subject,
                        htmlContent = newsletterSource.htmlContent,
                    )
                } catch (e: Exception) {
                    logger.error("Failed to parse newsletter content", e)
                    throw IllegalStateException("Failed to parse newsletter content: ${e.message}")
                }

            if (parsedContents.isEmpty()) {
                throw IllegalStateException("No content was parsed from the newsletter")
            }

            val createdContents = parseContents(newsletterSource, parsedContents)
            val contentsForProcessing = groupContentsForProcessing(newsletterSourceId, createdContents)

            logger.info("End complete newsletter processing for source ID: $newsletterSourceId")
            return contentsForProcessing.map { processExistingContent(it) }
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

        val newsletterName = resolveNewsletterName(newsletterSource)
        val contentProvider = resolveContentProvider(newsletterName)

        return parsedContents.map { mailContent ->
            // Extract original URL from RSS header if available, otherwise use parsed link
            val originalUrl = newsletterSource.headers["RSS-Item-URL"] ?: mailContent.link
            val imageUrl = mailContent.imageUrl ?: representativeImageUrlExtractorService.extractFromPage(originalUrl)

            val content =
                Content(
                    newsletterSourceId = newsletterSource.id,
                    title = mailContent.title,
                    content = mailContent.content,
                    newsletterName = newsletterName,
                    originalUrl = originalUrl,
                    imageUrl = imageUrl,
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

    private fun groupContentsForProcessing(
        newsletterSourceId: String,
        createdContents: List<Content>,
    ): List<Content> {
        val groupingService = newsletterContentGroupingServiceProvider.getIfAvailable() ?: return createdContents
        val groupedContents = groupingService.groupNewsletterSource(newsletterSourceId)

        logger.info(
            "Grouped newsletter source contents. sourceId={}, created={}, remainingForProcessing={}",
            newsletterSourceId,
            createdContents.size,
            groupedContents.size,
        )

        return groupedContents
    }

    private fun resolveNewsletterName(newsletterSource: NewsletterSource): String =
        newsletterSource.providerNameFromHint()
            ?: newsletterSource.sender.takeIf { sender -> sender.isNotBlank() }
            ?: newsletterSource.senderEmail

    private fun NewsletterSource.providerNameFromHint(): String? {
        val haystack = "${senderEmail.normalizedEmail()}\n${sender.lowercase()}"
        return PROVIDER_NAME_HINTS.entries.firstOrNull { (hint, _) -> haystack.contains(hint) }?.value
    }

    private fun String.normalizedEmail(): String = trim().lowercase()

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

    companion object {
        private const val MAX_CONTENT_LENGTH = 10_000 // 콘텐츠당 최대 길이 (약 20K-30K 토큰)

        private val PROVIDER_NAME_HINTS =
            linkedMapOf(
                "jsw@peterc.org" to "JavaScript Weekly",
                "javascript weekly" to "JavaScript Weekly",
                "newsletter@libhunt.com" to "Java Weekly",
                "java weekly" to "Java Weekly",
                "kotlinweekly.net" to "Kotlin Weekly",
                "kotlin weekly" to "Kotlin Weekly",
                "news@hada.io" to "GeekNews Weekly",
                "noreply@maeil-mail.kr" to "Maeil Mail",
                "kofearticle@substack.com" to "Korean FE Article",
                "dan@tldrnewsletter.com" to "TLDR",
                "eugen@baeldung.com" to "Baeldung",
                "yozm_help@wishket.com" to "Yozm",
                "tyler@ui.dev" to "Bytes",
                "submissions@webtoolsweekly.com" to "Web Tools Weekly",
                "submissions@vscode.email" to "VS Code Email",
                "pragmaticengineer@substack.com" to "The Pragmatic Engineer",
                "pragmaticengineer+deepdives@substack.com" to "The Pragmatic Engineer",
                "thepracticalstack461@substack.com" to "The Practical Stack",
                "thecodercafe+concepts@substack.com" to "The Coder Cafe",
                "architectureweekly@substack.com" to "Architecture Weekly",
                "fatbobman@substack.com" to "Fatbobman's Swift Weekly",
                "jacobbartlett@substack.com" to "Jacob's Tech Tavern",
                "react@cooperpress.com" to "React Status",
                "frontend@cooperpress.com" to "Frontend Focus",
                "node@cooperpress.com" to "Node Weekly",
                "postgres@cooperpress.com" to "Postgres Weekly",
                "peter@golangweekly.com" to "Go Weekly",
                "rahul@pythonweekly.com" to "Python Weekly",
                "contact@androidweekly.net" to "Android Weekly",
                "itworld@techlibrary.co.kr" to "ITWorld Korea",
                "css-weekly@beehiiv.com" to "CSS Weekly",
                "swiftwithvincent.com" to "Swift with Vincent",
                "ilbuntok.com" to "일분톡",
            )
    }
}
