package com.nexters.external.repository

import com.nexters.external.entity.FcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FcmTokenRepository : JpaRepository<FcmToken, Long> {
    fun findByUserIdAndIsActiveTrue(userId: Long): List<FcmToken>

    fun findByDeviceTokenAndIsActiveTrue(deviceToken: String): FcmToken?

    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.deviceToken = :deviceToken")
    fun deactivateToken(
        @Param("deviceToken") deviceToken: String
    ): Int

    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.userId = :userId")
    fun deactivateAllUserTokens(
        @Param("userId") userId: Long
    ): Int

    fun existsByUserIdAndDeviceTokenAndIsActiveTrue(
        userId: Long,
        deviceToken: String
    ): Boolean
}
