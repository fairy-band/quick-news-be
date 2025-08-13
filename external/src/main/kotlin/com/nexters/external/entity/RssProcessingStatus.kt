package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "rss_processing_status")
class RssProcessingStatus(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "newsletter_source_id", unique = true, nullable = false)
    val newsletterSourceId: String,
    @Column(name = "rss_url", nullable = false)
    val rssUrl: String,
    @Column(name = "item_url", unique = true, nullable = false)
    val itemUrl: String,
    @Column(name = "title", nullable = false)
    val title: String,
    @Column(name = "is_processed", nullable = false)
    var isProcessed: Boolean = false,
    @Column(name = "ai_processed", nullable = false)
    var aiProcessed: Boolean = false,
    @Column(name = "content_id")
    var contentId: Long? = null,
    @Column(name = "processing_error")
    var processingError: String? = null,
    @Column(name = "priority")
    val priority: Int = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
    @Column(name = "ai_processed_at")
    var aiProcessedAt: LocalDateTime? = null
)
