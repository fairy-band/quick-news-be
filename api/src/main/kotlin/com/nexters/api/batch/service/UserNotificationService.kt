package com.nexters.api.batch.service

import com.nexters.api.batch.config.AlarmIntegrationConfig.Companion.ALARM_TITLE
import com.nexters.api.batch.dto.BatchFcmRequest
import com.nexters.api.batch.dto.FcmNotificationMessage
import com.nexters.api.batch.dto.FcmUser
import com.nexters.api.batch.dto.NotificationType
import com.nexters.external.repository.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.integration.support.MessageBuilder
import org.springframework.messaging.MessageChannel
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class UserNotificationService(
    private val fcmBatchTriggerChannel: MessageChannel,
    private val fcmInputChannel: MessageChannel,
    private val fcmTokenRepository: FcmTokenRepository,
) {
    private val logger = LoggerFactory.getLogger(UserNotificationService::class.java)

    @Scheduled(
        cron = "\${notification.scheduler.weekly.cron:0 0 8 * * MON}",
        zone = "\${notification.scheduler.weekly.zone:Asia/Seoul}",
    )
    fun sendWeeklyNotification() {
        logger.info("주간 FCM 알림 전송 시작")

        try {
            val users =
                fcmTokenRepository
                    .findAllByIsActiveTrue()
                    .distinctBy { it.fcmToken }
                    .map {
                        FcmUser(
                            it.deviceToken,
                            it.fcmToken,
                        )
                    }

            val batchRequest =
                BatchFcmRequest(
                    users = users,
                    title = ALARM_TITLE,
                    notificationType = NotificationType.WEEKLY,
                )

            fcmBatchTriggerChannel.send(
                MessageBuilder
                    .withPayload(batchRequest)
                    .setHeader("source", "scheduled_weekly")
                    .build(),
            )

            logger.info("주간 FCM 알림 배치 요청 전송 완료")
        } catch (e: Exception) {
            logger.error("주간 FCM 알림 전송 중 오류 발생", e)
        }
    }

    fun sendSingleManualNotification(
        fcmToken: String,
        deviceToken: String,
        title: String = ALARM_TITLE,
    ) {
        logger.info("API용 수동 FCM 알림 단건 전송 시작")

        try {
            sendSingleFcmMessage(fcmToken, deviceToken, title, NotificationType.MANUAL, "manual_api")
            logger.info("API용 수동 FCM 알림 단건 전송 완료")
        } catch (e: Exception) {
            logger.error("API용 수동 FCM 알림 전송 중 오류 발생", e)
        }
    }

    private fun sendSingleFcmMessage(
        fcmToken: String,
        deviceToken: String,
        title: String,
        notificationType: NotificationType,
        source: String,
    ) {
        val fcmMessage =
            FcmNotificationMessage(
                user =
                    FcmUser(
                        deviceToken = deviceToken,
                        fcmToken = fcmToken,
                    ),
                title = title,
                notificationType = notificationType,
            )

        fcmInputChannel.send(
            MessageBuilder
                .withPayload(fcmMessage)
                .setHeader("source", source)
                .build(),
        )

        logger.debug("단일 FCM 메시지 전송: $fcmToken")
    }
}
