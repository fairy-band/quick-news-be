package com.nexters.external.service

import com.nexters.external.apiclient.DiscordWebhookClient
import com.nexters.external.dto.DiscordEmbed
import com.nexters.external.dto.DiscordEmbedField
import com.nexters.external.dto.DiscordEmbedFooter
import com.nexters.external.dto.DiscordWebhookRequest
import com.nexters.external.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class NotificationService(
    private val discordWebhookClient: DiscordWebhookClient,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    @Value("\${notification.discord.user-registration.webhook-url:}")
    private lateinit var userRegistrationWebhookUrl: String

    @Value("\${notification.discord.user-registration.enabled:false}")
    private var userRegistrationEnabled: Boolean = false

    fun notifyUserRegistration(
        userId: Long,
        deviceToken: String
    ) {
        if (!userRegistrationEnabled) {
            logger.debug("User registration notification is disabled")
            return
        }

        try {
            if (userRegistrationWebhookUrl.isBlank()) {
                logger.warn("User registration webhook URL is not configured")
                return
            }

            val embed = createUserRegistrationEmbed(userId, deviceToken)
            val request =
                DiscordWebhookRequest(
                    username = "유저알림봇",
                    embeds = listOf(embed),
                )

            val success = discordWebhookClient.sendMessage(userRegistrationWebhookUrl, request)
            if (success) {
                logger.info("User registration notification sent successfully for userId: $userId")
            } else {
                logger.warn("Failed to send user registration notification for userId: $userId, but continuing normal flow")
            }
        } catch (e: Exception) {
            // 알림 전송 실패가 비즈니스 로직에 영향을 주지 않도록 예외를 catch하고 로그만 남김
            logger.error(
                "Error occurred while sending user registration notification for userId: $userId. Service will continue normally.",
                e,
            )
        }
    }

    fun sendAnalyticsReport(reportContent: String): Boolean {
        return try {
            if (!userRegistrationEnabled) {
                logger.debug("User registration notification is disabled")
                return false
            }

            if (userRegistrationWebhookUrl.isBlank()) {
                logger.warn("User registration webhook URL is not configured for analytics report")
                return false
            }

            val request =
                DiscordWebhookRequest(
                    content = reportContent,
                    username = "Analytics Bot"
                )

            val success = discordWebhookClient.sendMessage(userRegistrationWebhookUrl, request)
            if (success) {
                logger.info("Analytics report sent successfully")
            } else {
                logger.warn("Failed to send analytics report")
            }
            success
        } catch (e: Exception) {
            logger.error("Error occurred while sending analytics report", e)
            false
        }
    }

    fun sendTestMessage(message: String): Boolean {
        return try {
            if (userRegistrationWebhookUrl.isBlank()) {
                logger.warn("User registration webhook URL is not configured for test message")
                return false
            }

            val request =
                DiscordWebhookRequest(
                    content = message,
                    username = "Analytics Bot"
                )

            val success = discordWebhookClient.sendMessage(userRegistrationWebhookUrl, request)
            if (success) {
                logger.info("Test message sent successfully")
            } else {
                logger.warn("Failed to send test message")
            }
            success
        } catch (e: Exception) {
            logger.error("Error occurred while sending test message", e)
            false
        }
    }

    private fun createUserRegistrationEmbed(
        userId: Long,
        deviceToken: String
    ): DiscordEmbed {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        val maskedDeviceToken =
            if (deviceToken.length > 8) {
                "${deviceToken.take(4)}****${deviceToken.takeLast(4)}"
            } else {
                "****"
            }

        val totalUserCount = userRepository.count()

        return DiscordEmbed(
            title = "🎉 새로운 사용자 등록",
            description = "Newsletter Feeder에 새로운 사용자가 등록되었습니다!",
            color = 5763719, // 파란색
            fields =
                listOf(
                    DiscordEmbedField(
                        name = "사용자 ID",
                        value = userId.toString(),
                        inline = true,
                    ),
                    DiscordEmbedField(
                        name = "디바이스 토큰",
                        value = maskedDeviceToken,
                        inline = true,
                    ),
                    DiscordEmbedField(
                        name = "총 사용자 수",
                        value = totalUserCount.toString(),
                        inline = true,
                    ),
                    DiscordEmbedField(
                        name = "등록 시간",
                        value = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        inline = false,
                    ),
                ),
            footer =
                DiscordEmbedFooter(
                    text = "Newsletter Feeder",
                ),
            timestamp = timestamp,
        )
    }
}
