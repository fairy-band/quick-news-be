package com.nexters.external.repository

import com.nexters.external.entity.UserExposedContentMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserExposedContentMappingRepository : JpaRepository<UserExposedContentMapping, Long> {
    @Modifying
    @Query("UPDATE UserExposedContentMapping u SET u.deleted = true WHERE u.userId = :userId AND DATE(u.createdAt) = DATE(:date)")
    fun markAsDeletedByUserIdAndDate(
        @Param("userId") userId: Long,
        @Param("date") date: LocalDateTime
    ): Int
}
