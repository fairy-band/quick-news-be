package com.nexters.newsletter.parser

import com.nexters.external.entity.NewsletterSource
import java.net.URI

data class MailParseContext(
    val content: String,
    val subject: String? = null,
    val htmlContent: String? = null,
    val webPageEnrichment: MailWebPageEnrichment = MailWebPageEnrichment(),
) {
    companion object {
        fun from(source: NewsletterSource): MailParseContext =
            MailParseContext(
                content = source.content,
                subject = source.subject,
                htmlContent = source.htmlContent,
                webPageEnrichment =
                    MailWebPageEnrichment(
                        items =
                            source.enrichment
                                ?.webPage
                                ?.items
                                .orEmpty()
                                .map { item ->
                                    MailWebPageEnrichmentItem(
                                        url = item.url,
                                        normalizedUrl = item.normalizedUrl,
                                        title = item.title,
                                        content = item.content,
                                        imageUrl = item.imageUrl,
                                        status = item.status,
                                    )
                                },
                    ),
            )
    }
}

data class MailWebPageEnrichment(
    val items: List<MailWebPageEnrichmentItem> = emptyList(),
) {
    fun successfulItems(): List<MailWebPageEnrichmentItem> =
        items
            .filter { item -> item.status.equals(ENRICHMENT_STATUS_SUCCESS, ignoreCase = true) }
            .filter { item -> item.url.isNotBlank() }
            .distinctBy { item -> item.matchingKey }

    fun successfulContentItems(): List<MailWebPageEnrichmentItem> =
        successfulItems()
            .filter { item -> !item.content.isNullOrBlank() }

    fun findSuccessfulContentItem(
        url: String,
        enrichmentKey: String? = null,
    ): MailWebPageEnrichmentItem? {
        val normalizedUrl = url.normalizedForEnrichment()
        val candidates = contentItems()

        return enrichmentKey
            ?.takeIf { key -> key.isNotBlank() }
            ?.let { key -> candidates.firstOrNull { item -> item.enrichmentKey == key } }
            ?: candidates.firstOrNull { item -> item.url.trim() == url.trim() }
            ?: candidates.firstOrNull { item -> item.matchingKey == normalizedUrl }
    }

    private fun contentItems(): List<MailWebPageEnrichmentItem> =
        items
            .filter { item -> item.status.equals(ENRICHMENT_STATUS_SUCCESS, ignoreCase = true) }
            .filter { item -> item.url.isNotBlank() }
            .filter { item -> !item.content.isNullOrBlank() }

    companion object {
        private const val ENRICHMENT_STATUS_SUCCESS = "success"
    }
}

data class MailWebPageEnrichmentItem(
    val url: String,
    val normalizedUrl: String? = null,
    val title: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val status: String,
) {
    val matchingKey: String
        get() = normalizedUrl?.trim()?.takeIf { url -> url.isNotBlank() } ?: url.normalizedForEnrichment()

    val enrichmentKey: String
        get() = "$matchingKey#${url.trim()}"
}

private val TRACKING_QUERY_KEYS =
    setOf(
        "fbclid",
        "gclid",
        "mc_cid",
        "mc_eid",
    )

private fun String.normalizedForEnrichment(): String {
    val value = trim()
    return runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return@runCatching value.substringBefore("#").trimEnd('/')
        val host = uri.host?.lowercase() ?: return@runCatching value.substringBefore("#").trimEnd('/')
        val path =
            uri.rawPath
                .orEmpty()
                .ifBlank { "/" }
                .trimEnd('/')
                .ifBlank { "/" }
        val rawQuery = uri.rawQuery
        val queryParameters =
            if (rawQuery == null) {
                emptyList()
            } else {
                rawQuery
                    .split("&")
                    .filter { parameter ->
                        val key = parameter.substringBefore("=").lowercase()
                        !key.startsWith("utm_") && key !in TRACKING_QUERY_KEYS
                    }.sorted()
            }
        val queryText = queryParameters.joinToString("&")
        val query = if (queryText.isBlank()) null else queryText

        URI(scheme, null, host, uri.port, path, query, null).toString()
    }.getOrElse {
        value.substringBefore("#").trimEnd('/')
    }
}
