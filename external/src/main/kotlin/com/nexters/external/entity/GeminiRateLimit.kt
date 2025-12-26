package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    name = "gemini_rate_limit",
    indexes = [
        Index(name = "idx_model_date", columnList = "model_name,limit_date", unique = true)
    ]
)
class GeminiRateLimit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "model_name", nullable = false, length = 100)
    val modelName: String,
    @Column(name = "limit_date", nullable = false)
    val limitDate: LocalDate,
    @Column(name = "request_count", nullable = false)
    var requestCount: Int = 0,
    @Column(name = "max_requests_per_day", nullable = false)
    val maxRequestsPerDay: Int = 20
)
