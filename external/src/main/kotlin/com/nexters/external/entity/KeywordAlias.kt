package com.nexters.external.entity

import com.nexters.external.enums.KeywordAliasMatchType
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
@Table(name = "keyword_aliases")
class KeywordAlias(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "keyword_id", nullable = false)
    val keywordId: Long,
    @Column(nullable = false, columnDefinition = "TEXT")
    val alias: String,
    @Column(name = "normalized_alias", nullable = false, columnDefinition = "TEXT")
    val normalizedAlias: String = alias.trim().lowercase(),
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    val matchType: KeywordAliasMatchType = KeywordAliasMatchType.PHRASE,
    @Column(nullable = false)
    val weight: Double = 1.0,
    @Column(name = "target_fields", nullable = false)
    val targetFields: String = "TITLE,CONTENT,URL,SOURCE",
    @Column(name = "case_sensitive", nullable = false)
    val caseSensitive: Boolean = false,
    @Column(nullable = false)
    val enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
