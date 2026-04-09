package com.nexters.external.entity

import com.nexters.external.enums.ContentGenerationMode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "content_generation_attempts")
class ContentGenerationAttempt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    val content: Content,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "generation_mode")
    val generationMode: ContentGenerationMode,
    @Column(nullable = false, name = "attempt_number")
    val attemptNumber: Int,
    @Column(nullable = false)
    val model: String,
    @Column(nullable = false, name = "prompt_version")
    val promptVersion: String,
    @Column(nullable = false, columnDefinition = "TEXT", name = "generated_summary")
    val generatedSummary: String,
    @Column(nullable = false, columnDefinition = "TEXT", name = "generated_headlines")
    val generatedHeadlines: String,
    @Column(nullable = false, columnDefinition = "TEXT", name = "matched_keywords")
    val matchedKeywords: String,
    @Column(nullable = false, name = "quality_score")
    val qualityScore: Int,
    @Column(nullable = false, columnDefinition = "TEXT", name = "quality_reason")
    val qualityReason: String,
    @Column(nullable = false, columnDefinition = "TEXT", name = "ai_like_patterns")
    val aiLikePatterns: String,
    @Column(nullable = false, columnDefinition = "TEXT", name = "recommended_fix")
    val recommendedFix: String,
    @Column(nullable = false)
    val passed: Boolean,
    @Column(nullable = false)
    val accepted: Boolean,
    @Column(nullable = false, name = "retry_count")
    val retryCount: Int,
    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
