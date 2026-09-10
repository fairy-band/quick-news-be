package com.nexters.newsletter.service

import com.nexters.external.entity.Content
import com.nexters.external.service.ContentProcessingStateService
import com.nexters.external.service.ContentSourceValidator
import com.nexters.external.enums.ContentProcessingStage
import com.nexters.newsletter.parser.MailContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI

/**
 * A deterministic, write-time guard. It compares only the parser output used
 * for this ingestion run with the Content rows just written in that same run.
 */
@Service
class NewsletterIngestionIntegrityService(
    private val processingStateService: ContentProcessingStateService,
) {
    private val logger = LoggerFactory.getLogger(NewsletterIngestionIntegrityService::class.java)

    fun verify(
        sourceId: String,
        parsed: List<MailContent>,
        stored: List<Content>,
        fallbackUrl: String,
    ): Boolean {
        val expected = parsed
            .filterNot { ContentSourceValidator.isInvalidSource(it.content) }
            .filterNot { com.nexters.external.filter.AdAndPromotionalFilter.isPromotional(it.title, it.content) }
            .map { key(it.title, it.link.ifBlank { fallbackUrl }) }
            .sorted()
        val actual = stored.map { key(it.title, it.originalUrl) }.sorted()
        val invalidStored = stored.any { ContentSourceValidator.isInvalidSource(it.content) }

        if (!invalidStored && expected == actual) {
            return true
        }

        val message = "Newsletter ingestion integrity mismatch. sourceId=$sourceId expected=${expected.size} stored=${actual.size} invalidStored=$invalidStored"
        logger.error(message)
        stored.forEach { processingStateService.failed(requireNotNull(it.id), ContentProcessingStage.AI, IllegalStateException(message)) }
        return false
    }

    private fun key(title: String, url: String): String = "${normalizeTitle(title)}|${normalizeUrl(url)}"

    private fun normalizeTitle(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private fun normalizeUrl(value: String): String =
        runCatching {
            val uri = URI(value.trim())
            val retainedQuery = uri.rawQuery
                ?.split("&")
                ?.filterNot { it.substringBefore('=').lowercase().startsWith("utm_") || it.substringBefore('=').lowercase() in TRACKING_QUERY_KEYS }
                ?.sorted()
                ?.joinToString("&")
            URI(uri.scheme?.lowercase(), uri.authority?.lowercase(), uri.path?.trimEnd('/'), retainedQuery, null).toString()
        }.getOrElse { value.trim().trimEnd('/').lowercase() }

    companion object {
        private val TRACKING_QUERY_KEYS = setOf("fbclid", "gclid", "mc_cid", "mc_eid")
    }
}
