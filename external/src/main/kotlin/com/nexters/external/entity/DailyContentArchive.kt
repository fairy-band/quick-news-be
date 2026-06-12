package com.nexters.external.entity

import com.nexters.external.enums.ContentProviderType
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDate
import java.time.LocalDateTime

@Document(collection = "daily_content_archive")
@CompoundIndex(name = "user_date_idx", def = "{'user._id': 1, 'date': 1}", unique = true)
data class DailyContentArchive(
    @Id
    val id: String? = null,
    val date: LocalDate,
    val user: UserSnapshot,
    val exposureContents: List<ExposureContentSnapshot>,
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

    data class ExposureContentSnapshot(
        @Field("_id")
        val id: Long,
        val content: ContentSnapshot,
        val provocativeKeyword: String,
        val provocativeHeadline: String,
        val summaryContent: String,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    )

    data class ContentSnapshot(
        @Field("_id")
        val id: Long,
        val originalUrl: String,
        val imageUrl: String? = null,
        val newsletterName: String,
        val contentProvider: ContentProviderSnapshot? = null,
    )

    data class ContentProviderSnapshot(
        @Field("_id")
        val id: Long? = null,
        val language: String? = null,
        val type: ContentProviderType? = null,
    )
}
