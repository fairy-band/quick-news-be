package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "exposure_content_markdowns")
class ExposureContentMarkdown(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, name = "exposure_content_id")
    val exposureContentId: Long,
    @Column(nullable = false, columnDefinition = "TEXT", name = "markdown_content")
    val markdownContent: String,
    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
