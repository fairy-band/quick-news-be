package com.nexters.external.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
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
    val plainText: String?,
    val htmlText: String?,
    @Deprecated("Use plainText or htmlText instead", ReplaceWith("plainText"))
    val content: String? = null, // 하위호환성을 위해 유지
    val contentType: String,
    val receivedDate: LocalDateTime,
    val headers: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList(),
    @CreatedDate
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    val updatedAt: LocalDateTime? = null
)

data class Attachment(
    val filename: String,
    val contentType: String,
    val size: Long,
    val data: ByteArray? = null
)
