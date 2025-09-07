package com.nexters.external.repository

import com.nexters.external.entity.FcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FcmTokenRepository : JpaRepository<FcmToken, Long> {
    fun findByDeviceTokenAndIsActiveTrue(deviceToken: String): FcmToken?

    fun findByFcmTokenAndIsActiveTrue(fcmToken: String): FcmToken?

    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.fcmToken = :fcmToken")
    fun deactivateToken(
        @Param("fcmToken") fcmToken: String
    ): Int

    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.deviceToken = :deviceToken")
    fun deactivateDeviceToken(
        @Param("deviceToken") deviceToken: String
    ): Int

    fun existsByDeviceTokenAndFcmTokenAndIsActiveTrue(
        deviceToken: String,
        fcmToken: String
    ): Boolean

    fun findAllByIsActiveTrue(): List<FcmToken>
}
