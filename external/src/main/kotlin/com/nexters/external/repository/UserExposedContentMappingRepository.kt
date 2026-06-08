package com.nexters.external.repository

import com.nexters.external.entity.UserExposedContentMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserExposedContentMappingRepository : JpaRepository<UserExposedContentMapping, Long> {
    @Query(
        """
        SELECT u FROM UserExposedContentMapping u
        WHERE u.userId = :userId
        AND u.createdAt >= :startAt
        AND u.createdAt < :endAt
        AND u.deleted = false
    """
    )
    fun findActiveByUserIdAndCreatedAtRange(
        @Param("userId") userId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
    ): List<UserExposedContentMapping>

    @Modifying
    @Query(
        """
        UPDATE UserExposedContentMapping u
        SET u.deleted = true
        WHERE u.userId = :userId
        AND u.createdAt >= :startAt
        AND u.createdAt < :endAt
        AND u.deleted = false
    """
    )
    fun markActiveAsDeletedByUserIdAndCreatedAtRange(
        @Param("userId") userId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
    ): Int

    @Query(
        """
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
        FROM UserExposedContentMapping u
        WHERE u.userId = :userId
        AND u.createdAt >= :startAt
        AND u.createdAt < :endAt
        AND u.deleted = true
    """
    )
    fun existsDeletedByUserIdAndCreatedAtRange(
        @Param("userId") userId: Long,
        @Param("startAt") startAt: LocalDateTime,
        @Param("endAt") endAt: LocalDateTime,
    ): Boolean
}
