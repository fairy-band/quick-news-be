package com.nexters.external.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate
import java.time.LocalDateTime

@Document(collection = "daily_content_archive")
data class DailyContentArchive(
    @Id
    val id: String? = null,
    val date: LocalDate,
    val user: UserSnapshot,
    val exposureContents: List<ExposureContent>,
    @CreatedDate
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    val updatedAt: LocalDateTime? = null
) {
    data class UserSnapshot(
        val id: Long,
        val deviceToken: String,
        val categories: List<Long> = emptyList(), // Category IDs
        val keywords: List<Long> = emptyList(), // Keyword IDs
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    ) {
        companion object {
            fun from(user: User): UserSnapshot =
                UserSnapshot(
                    id = user.id ?: throw IllegalArgumentException("User ID cannot be null"),
                    deviceToken = user.deviceToken,
                    categories = user.categories.mapNotNull { it.id },
                    keywords = user.keywords.mapNotNull { it.id },
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt
                )
        }
    }
}
