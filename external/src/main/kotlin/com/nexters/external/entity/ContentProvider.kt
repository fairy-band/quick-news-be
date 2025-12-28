package com.nexters.external.entity

import com.nexters.external.enums.ContentProviderType
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
@Table(name = "content_provider")
class ContentProvider(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val channel: String,
    @Column(nullable = false, length = 10)
    val language: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 20)
    val type: ContentProviderType?, // 이전 하위호환을 위해 nullable을 허용, mongo deserialize 할 때 null일 수 있음
    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(nullable = false, name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
