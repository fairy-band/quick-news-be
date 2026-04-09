package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "content_provider_requests")
class ContentProviderRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, name = "content_provider_name")
    val contentProviderName: String,
    @Column(nullable = false)
    val channel: String,
    @Column(nullable = false, name = "request_category", length = 20)
    val requestCategory: String,
    @Column(nullable = false, name = "related_to")
    val relatedTo: String,
    @Column(nullable = true)
    val reason: String? = null,
    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
