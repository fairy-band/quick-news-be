package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "email_sources")
class EmailSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    var name: String,
    @Column(name = "sender_email", nullable = false, unique = true)
    var senderEmail: String,
    @Column(name = "parser_name")
    var parserName: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "category_id")
    var categoryId: Long? = null,
    @Column(nullable = false)
    var priority: Int = 5,
    @Column(name = "last_received_at")
    var lastReceivedAt: LocalDateTime? = null,
    @Column(nullable = false)
    var status: String = "HEALTHY",
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
