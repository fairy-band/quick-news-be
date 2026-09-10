package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "gemini_backoff_state",
    indexes = [
        Index(name = "idx_gemini_backoff_model_type", columnList = "model_name,limit_type", unique = true),
        Index(name = "idx_gemini_backoff_until", columnList = "blocked_until"),
    ],
)
class GeminiBackoffState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "model_name", nullable = false, length = 100)
    val modelName: String,
    @Column(name = "limit_type", nullable = false, length = 50)
    val limitType: String,
    @Column(name = "consecutive_failures", nullable = false)
    var consecutiveFailures: Int = 0,
    @Column(name = "blocked_until")
    var blockedUntil: LocalDateTime? = null,
    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
