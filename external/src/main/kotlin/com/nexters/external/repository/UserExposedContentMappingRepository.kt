package com.nexters.external.repository

import com.nexters.external.entity.UserExposedContentMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface UserExposedContentMappingRepository : JpaRepository<UserExposedContentMapping, Long> {
    @Query("SELECT u FROM UserExposedContentMapping u WHERE u.userId = :userId AND DATE(u.createdAt) = DATE(:date) AND u.deleted = false")
    fun findByUserIdAndDate(
        @Param("userId") userId: Long,
        @Param("date") date: LocalDateTime
    ): List<UserExposedContentMapping>

    @Query("SELECT u FROM UserExposedContentMapping u WHERE u.userId = :userId AND DATE(u.createdAt) = :date AND u.deleted = true")
    fun findDeletedByUserIdAndDate(
        @Param("userId") userId: Long,
        @Param("date") date: LocalDate
    ): List<UserExposedContentMapping>
}
