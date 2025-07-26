package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "contents")
class Content(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = true, name = "newsletter_source_id")
    val newsletterSourceId: String? = null,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(nullable = false, name = "newsletter_name")
    val newsletterName: String,
    @Column(nullable = false, name = "original_url")
    val originalUrl: String,
    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
