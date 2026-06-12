package com.nexters.api.batch.service

import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillFailure
import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillRequest
import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillResponse
import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillSample
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.enums.ContentProviderType
import com.nexters.external.repository.ContentRepository
import com.nexters.external.service.ContentService
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletter.parser.MailContent
import com.nexters.newsletter.parser.MailParseContext
import com.nexters.newsletter.parser.MailParser
import com.nexters.newsletter.parser.MailParserFactory
import jakarta.mail.internet.MimeUtility
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NewsletterParseOnlyBackfillService(
    private val newsletterSourceService: NewsletterSourceService,
    private val contentRepository: ContentRepository,
    private val contentService: ContentService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mailParserFactory = MailParserFactory()

    fun createContents(request: NewsletterParseOnlyBackfillRequest): NewsletterParseOnlyBackfillResponse {
        val targetSenders = request.normalizedTargetSenders()
        val sources = newsletterSourceService.findAll()
        var targetSources = 0
        var skippedNoParser = 0

        val candidates =
            sources
                .asSequence()
                .filter { source -> source.id != null }
                .filter { source -> targetSenders == null || source.senderEmail.normalizedEmail() in targetSenders }
                .onEach { targetSources++ }
                .mapNotNull { source ->
                    val parser = mailParserFactory.findParser(source.senderEmail, source.subject)
                    if (parser == null) {
                        skippedNoParser++
                        null
                    } else {
                        SourceWithParser(source = source, parser = parser)
                    }
                }.sortedBy { candidate -> candidate.source.receivedDate }
                .limitedTo(request.limit)
                .toList()

        val existingSourceIds = findExistingSourceIds(candidates, request.force)
        val seenOriginalUrls = mutableSetOf<String>()
        val samples = mutableListOf<NewsletterParseOnlyBackfillSample>()
        val failures = mutableListOf<NewsletterParseOnlyBackfillFailure>()
        val createdContentIds = mutableListOf<Long>()
        var skippedExistingSource = 0
        var skippedInvalidContent = 0
        var skippedDuplicateUrl = 0
        var parseEmptySources = 0
        var parseFailedSources = 0
        var createdContents = 0
        var wouldCreateContents = 0

        candidates.forEach { candidate ->
            val source = candidate.source
            val sourceId = source.id
            if (sourceId == null) {
                skippedInvalidContent++
                return@forEach
            }

            if (!request.force && sourceId in existingSourceIds) {
                skippedExistingSource++
                return@forEach
            }

            val parsedContents =
                try {
                    candidate.parser.parse(MailParseContext.from(source))
                } catch (e: Exception) {
                    parseFailedSources++
                    failures.addFailure(source, "parse failed: ${e.message}")
                    logger.warn("Failed to parse newsletter source: $sourceId", e)
                    return@forEach
                }

            if (parsedContents.isEmpty()) {
                parseEmptySources++
                failures.addFailure(source, "parser returned empty contents")
                return@forEach
            }

            parsedContents.forEach { mailContent ->
                val originalUrl = resolveOriginalUrl(source, mailContent)
                if (mailContent.title.isBlank() || mailContent.content.isBlank() || originalUrl.isBlank()) {
                    skippedInvalidContent++
                    return@forEach
                }

                val shouldCheckDuplicateUrl = originalUrl.shouldCheckDuplicateUrl()
                if (!request.force && shouldCheckDuplicateUrl && !seenOriginalUrls.add(originalUrl)) {
                    skippedDuplicateUrl++
                    return@forEach
                }

                if (!request.force && shouldCheckDuplicateUrl && contentRepository.existsByOriginalUrl(originalUrl)) {
                    skippedDuplicateUrl++
                    return@forEach
                }

                samples.addSample(source, mailContent, originalUrl)

                if (request.dryRun) {
                    wouldCreateContents++
                } else {
                    val content =
                        contentService.createContent(
                            title = mailContent.title,
                            content = mailContent.content,
                            contentProviderName = resolveProviderName(source),
                            originalUrl = originalUrl,
                            publishedAt = source.receivedDate.toLocalDate(),
                            newsletterSourceId = sourceId,
                            imageUrl = mailContent.imageUrl,
                            contentProviderType = ContentProviderType.NEWSLETTER,
                        )

                    createdContents++
                    content.id?.let { createdContentIds += it }
                }
            }
        }

        return NewsletterParseOnlyBackfillResponse(
            dryRun = request.dryRun,
            force = request.force,
            targetAllParsers = targetSenders == null,
            senderEmails = targetSenders.orEmpty(),
            scannedSources = sources.size,
            targetSources = targetSources,
            candidateSources = candidates.size,
            skippedNoParser = skippedNoParser,
            skippedExistingSource = skippedExistingSource,
            skippedInvalidContent = skippedInvalidContent,
            skippedDuplicateUrl = skippedDuplicateUrl,
            parseEmptySources = parseEmptySources,
            parseFailedSources = parseFailedSources,
            createdContents = createdContents,
            wouldCreateContents = wouldCreateContents,
            createdContentIds = createdContentIds.take(SAMPLE_LIMIT),
            samples = samples,
            failures = failures,
        )
    }

    private fun findExistingSourceIds(
        candidates: List<SourceWithParser>,
        force: Boolean,
    ): Set<String> {
        if (force) return emptySet()

        val sourceIds = candidates.mapNotNull { candidate -> candidate.source.id }
        if (sourceIds.isEmpty()) return emptySet()

        return sourceIds
            .chunked(SOURCE_LOOKUP_CHUNK_SIZE)
            .flatMap { chunk -> contentRepository.findLookupRowsByNewsletterSourceIds(chunk) }
            .mapNotNull { row -> row.newsletterSourceId }
            .toSet()
    }

    private fun Sequence<SourceWithParser>.limitedTo(limit: Int?): Sequence<SourceWithParser> =
        limit
            ?.coerceAtLeast(0)
            ?.let { take(it) }
            ?: this

    private fun NewsletterParseOnlyBackfillRequest.normalizedTargetSenders(): Set<String>? =
        senderEmails
            ?.map { sender -> sender.normalizedEmail() }
            ?.filter { sender -> sender.isNotBlank() }
            ?.toSet()
            ?.takeIf { senders -> senders.isNotEmpty() }

    private fun resolveOriginalUrl(
        source: NewsletterSource,
        mailContent: MailContent,
    ): String =
        mailContent.link
            .trim()
            .ifBlank { source.headers["RSS-Item-URL"].orEmpty().trim() }

    private fun resolveProviderName(source: NewsletterSource): String =
        source.providerNameFromHint()
            ?: source.sender.decodeMimeText().takeIf { sender -> sender.isNotBlank() }
            ?: source.senderEmail

    private fun NewsletterSource.providerNameFromHint(): String? {
        val haystack = "${senderEmail.normalizedEmail()}\n${sender.decodeMimeText().lowercase()}"
        return PROVIDER_NAME_HINTS.entries.firstOrNull { (hint, _) -> haystack.contains(hint) }?.value
    }

    private fun MutableList<NewsletterParseOnlyBackfillSample>.addSample(
        source: NewsletterSource,
        mailContent: MailContent,
        originalUrl: String,
    ) {
        if (size >= SAMPLE_LIMIT) return

        val sourceId = source.id ?: return
        this +=
            NewsletterParseOnlyBackfillSample(
                sourceId = sourceId,
                senderEmail = source.senderEmail,
                subject = source.subject,
                title = mailContent.title,
                originalUrl = originalUrl,
            )
    }

    private fun MutableList<NewsletterParseOnlyBackfillFailure>.addFailure(
        source: NewsletterSource,
        reason: String,
    ) {
        if (size >= SAMPLE_LIMIT) return

        this +=
            NewsletterParseOnlyBackfillFailure(
                sourceId = source.id,
                senderEmail = source.senderEmail,
                subject = source.subject,
                reason = reason,
            )
    }

    private fun String.normalizedEmail(): String = trim().lowercase()

    private fun String.decodeMimeText(): String =
        try {
            MimeUtility
                .decodeText(this)
                .trim()
                .trim('"')
        } catch (e: Exception) {
            trim().trim('"')
        }

    private fun String.shouldCheckDuplicateUrl(): Boolean {
        val normalized = trim().trimEnd('/').lowercase()
        return normalized !in FALLBACK_ORIGINAL_URLS
    }

    private data class SourceWithParser(
        val source: NewsletterSource,
        val parser: MailParser,
    )

    companion object {
        private const val SAMPLE_LIMIT = 20
        private const val SOURCE_LOOKUP_CHUNK_SIZE = 500

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

        private val FALLBACK_ORIGINAL_URLS =
            setOf(
                "https://www.maeil-mail.kr",
                "https://substack.com",
                "https://weekly.fatbobman.com",
                "https://blog.jacobstechtavern.com",
            )
    }
}
