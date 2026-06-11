package com.nexters.external.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.lang.Nullable
import java.time.LocalDateTime

@Document(collection = "newsletter_sources")
data class NewsletterSource(
    @Id
    val id: String? = null,
    val subject: String?,
    val sender: String,
    val senderEmail: String,
    val recipient: String,
    val recipientEmail: String,
    val content: String,
    val contentType: String,
    @Nullable
    val htmlContent: String? = null,
    val receivedDate: LocalDateTime,
    val headers: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList(),
    val enrichment: NewsletterSourceEnrichment? = null,
    @CreatedDate
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    val updatedAt: LocalDateTime? = null
)

data class NewsletterSourceEnrichment(
    val webPage: WebPageEnrichment? = null,
)

data class WebPageEnrichment(
    val version: Int = 1,
    val status: String? = null,
    val processedAt: LocalDateTime? = null,
    val items: List<WebPageEnrichmentItem> = emptyList(),
)

data class WebPageEnrichmentItem(
    val url: String,
    val normalizedUrl: String? = null,
    val title: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val status: String,
    val reason: String? = null,
    val fetchedAt: LocalDateTime? = null,
    val contentHash: String? = null,
)

data class Attachment(
    val filename: String,
    val contentType: String,
    val size: Long,
    val data: ByteArray? = null
)
