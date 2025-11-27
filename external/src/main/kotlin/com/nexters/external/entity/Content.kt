package com.nexters.external.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate
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
    @Column(nullable = true, name = "published_at")
    val publishedAt: LocalDate,
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "content_provider_id")
    @JsonIgnoreProperties("categories")
    var contentProvider: ContentProvider? = null,
    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "content_keyword_mappings",
        joinColumns = [JoinColumn(name = "content_id")],
        inverseJoinColumns = [JoinColumn(name = "keyword_id")]
    )
    val reservedKeywords: Set<ReservedKeyword> = emptySet()
)
