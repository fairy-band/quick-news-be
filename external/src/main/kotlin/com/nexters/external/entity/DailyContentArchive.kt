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
    val user: User,
    val exposureContents: List<ExposureContent>,
    @CreatedDate
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    val updatedAt: LocalDateTime? = null
)
