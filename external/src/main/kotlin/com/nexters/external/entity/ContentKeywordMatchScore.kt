package com.nexters.external.entity

import com.nexters.external.enums.KeywordAliasMatchType
import com.nexters.external.enums.KeywordMatchSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "content_keyword_match_scores")
class ContentKeywordMatchScore(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "content_id", nullable = false)
    val contentId: Long,
    @Column(name = "keyword_id", nullable = false)
    val keywordId: Long,
    @Column(nullable = false)
    var score: Double,
    @Column(nullable = false)
    var confidence: Double,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val source: KeywordMatchSource,
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    val matchType: KeywordAliasMatchType,
    @Column(name = "matched_text", columnDefinition = "TEXT")
    var matchedText: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var reason: String,
    @Column(nullable = false)
    var accepted: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
