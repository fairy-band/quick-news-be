package com.nexters.external.service

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.nexters.external.entity.DeviceType
import com.nexters.external.entity.FcmToken
import com.nexters.external.repository.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PushNotificationService(
    private val fcmTokenRepository: FcmTokenRepository
) {
    private val logger = LoggerFactory.getLogger(PushNotificationService::class.java)

    /**
     * 특정 사용자에게 알림 발송
     */
    fun sendToUser(
        userId: Long,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        val tokens = fcmTokenRepository.findByUserIdAndIsActiveTrue(userId)

        if (tokens.isEmpty()) {
            logger.warn("No active tokens found for user: $userId")
            return false
        }

        var successCount = 0
        tokens.forEach { token ->
            if (sendNotification(token.fcmToken, title, body, data)) {
                successCount++
            }
        }

        logger.info("Sent notification to $successCount/${tokens.size} devices for user: $userId")
        return successCount > 0
    }

    /**
     * 단일 토큰으로 알림 발송
     */
    fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean =
        try {
            val notification =
                Notification
                    .builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()

            val messageBuilder =
                Message
                    .builder()
                    .setToken(token)
                    .setNotification(notification)

            // 추가 데이터가 있으면 포함
            if (data.isNotEmpty()) {
                messageBuilder.putAllData(data)
            }

            val message = messageBuilder.build()
            val response = FirebaseMessaging.getInstance().send(message)

            logger.info("Successfully sent message: $response")
            true
        } catch (e: Exception) {
            logger.error("Failed to send notification to token: $token", e)
            // 토큰이 유효하지 않은 경우 비활성화
            if (isTokenInvalid(e)) {
                fcmTokenRepository.deactivateToken(token)
                logger.info("Deactivated invalid token: $token")
            }
            false
        }

    /**
     * FCM 토큰 등록/갱신
     */
    fun registerToken(
        userId: Long,
        fcmToken: String,
        deviceType: DeviceType
    ): Boolean =
        try {
            // 기존 토큰이 있는지 확인
            val existingToken = fcmTokenRepository.findByFcmTokenAndIsActiveTrue(fcmToken)

            if (existingToken == null) {
                // 새 토큰 등록
                val newToken =
                    FcmToken(
                        userId = userId,
                        fcmToken = fcmToken,
                        deviceType = deviceType
                    )
                fcmTokenRepository.save(newToken)
                logger.info("New FCM token registered for user: $userId, deviceType: $deviceType")
            } else {
                logger.info("FCM token already exists for user: $userId")
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to register FCM token for user: $userId", e)
            false
        }

    /**
     * 토큰 해제
     */
    fun unregisterToken(fcmToken: String): Boolean =
        try {
            val deactivatedCount = fcmTokenRepository.deactivateToken(fcmToken)
            logger.info("Deactivated $deactivatedCount token(s)")
            deactivatedCount > 0
        } catch (e: Exception) {
            logger.error("Failed to unregister FCM token: $fcmToken", e)
            false
        }

    /**
     * 사용자의 모든 토큰 해제
     */
    fun unregisterAllUserTokens(userId: Long): Boolean =
        try {
            val deactivatedCount = fcmTokenRepository.deactivateAllUserTokens(userId)
            logger.info("Deactivated $deactivatedCount token(s) for user: $userId")
            deactivatedCount > 0
        } catch (e: Exception) {
            logger.error("Failed to unregister all tokens for user: $userId", e)
            false
        }

    /**
     * 토큰이 유효하지 않은지 확인
     */
    private fun isTokenInvalid(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("registration-token-not-registered") ||
            message.contains("invalid-registration-token") ||
            message.contains("not-found")
    }
}
