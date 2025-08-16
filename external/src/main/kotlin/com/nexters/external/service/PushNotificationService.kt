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
     * 특정 디바이스에 알림 발송
     */
    fun sendToDevice(
        deviceToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        val token = fcmTokenRepository.findByDeviceTokenAndIsActiveTrue(deviceToken)

        if (token == null) {
            logger.warn("No active token found for device: $deviceToken")
            return false
        }

        return sendNotification(token.fcmToken, title, body, data)
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
     * 디바이스 토큰을 기준으로 FCM 토큰을 등록하거나 업데이트합니다.
     */
    fun registerToken(
        deviceToken: String,
        fcmToken: String,
        deviceType: DeviceType
    ): Boolean =
        try {
            // 디바이스 토큰으로 기존 레코드 찾기
            val existingToken = fcmTokenRepository.findByDeviceTokenAndIsActiveTrue(deviceToken)

            if (existingToken != null) {
                // 디바이스 토큰이 이미 존재하는 경우
                if (existingToken.fcmToken == fcmToken) {
                    // FCM 토큰도 같으면 이미 등록된 상태 - 마지막 접근 시간만 업데이트
                    logger.info("FCM token already registered for device: $deviceToken - updating last access time")
                    // 기존 토큰의 updated_at을 갱신하기 위해 저장 (Hibernate가 @UpdateTimestamp 처리)
                    fcmTokenRepository.save(existingToken)
                } else {
                    // FCM 토큰이 다르면 업데이트 (기존 토큰 비활성화 후 새로 등록)
                    fcmTokenRepository.deactivateDeviceToken(deviceToken)
                    logger.info("Updated FCM token for existing device: $deviceToken")
                    
                    val newToken = FcmToken(
                        deviceToken = deviceToken,
                        fcmToken = fcmToken,
                        deviceType = deviceType
                    )
                    fcmTokenRepository.save(newToken)
                    logger.info("New FCM token registered for device: $deviceToken, deviceType: $deviceType")
                }
            } else {
                // 디바이스 토큰이 없는 경우 - 새로운 등록
                // 하지만 FCM 토큰이 다른 디바이스에 등록되어 있을 수 있으므로 체크
                val existingFcmToken = fcmTokenRepository.findByFcmTokenAndIsActiveTrue(fcmToken)
                if (existingFcmToken != null) {
                    // FCM 토큰이 다른 디바이스에 등록되어 있으면 비활성화
                    fcmTokenRepository.deactivateToken(fcmToken)
                    logger.info("Deactivated FCM token from other device: ${existingFcmToken.deviceToken}")
                }
                
                val newToken = FcmToken(
                    deviceToken = deviceToken,
                    fcmToken = fcmToken,
                    deviceType = deviceType
                )
                fcmTokenRepository.save(newToken)
                logger.info("New FCM token registered for new device: $deviceToken, deviceType: $deviceType")
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to register FCM token for device: $deviceToken", e)
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
     * 디바이스 토큰 해제
     */
    fun unregisterDeviceToken(deviceToken: String): Boolean =
        try {
            val deactivatedCount = fcmTokenRepository.deactivateDeviceToken(deviceToken)
            logger.info("Deactivated $deactivatedCount token(s) for device: $deviceToken")
            deactivatedCount > 0
        } catch (e: Exception) {
            logger.error("Failed to unregister device token: $deviceToken", e)
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
