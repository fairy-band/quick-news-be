package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "content_category_scores",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_content_category_scores_content_category", columnNames = ["content_id", "category_id"]),
    ],
)
class ContentCategoryScore(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "content_id", nullable = false)
    val contentId: Long,
    @Column(name = "category_id", nullable = false)
    val categoryId: Long,
    @Column(name = "keyword_score", nullable = false)
    val keywordScore: Double,
    @Column(name = "provider_score", nullable = false)
    val providerScore: Double,
    @Column(name = "total_score", nullable = false)
    val totalScore: Double,
    @Column(name = "competing_category_id")
    val competingCategoryId: Long? = null,
    @Column(name = "competing_score", nullable = false)
    val competingScore: Double = 0.0,
    @Column(name = "provider_mismatch", nullable = false)
    val providerMismatch: Boolean,
    @Column(name = "is_single_category_fit", nullable = false)
    val singleCategoryFit: Boolean,
    @Column(name = "calculation_version", nullable = false, length = 50)
    val calculationVersion: String,
    @Column(name = "calculated_at", nullable = false)
    val calculatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
