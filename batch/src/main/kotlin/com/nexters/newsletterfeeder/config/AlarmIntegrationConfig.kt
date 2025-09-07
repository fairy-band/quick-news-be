package com.nexters.newsletterfeeder.config

import com.nexters.external.service.AlarmMessageResolver
import com.nexters.external.service.PushNotificationService
import com.nexters.newsletterfeeder.dto.BatchFcmRequest
import com.nexters.newsletterfeeder.dto.FcmNotificationMessage
import com.nexters.newsletterfeeder.dto.FcmNotificationResult
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.support.MessageBuilder
import org.springframework.messaging.MessageChannel

@Configuration
@EnableIntegration
class AlarmIntegrationConfig(
    private val pushNotificationService: PushNotificationService,
    private val alarmMessageResolver: AlarmMessageResolver,
) {
    private val logger = LoggerFactory.getLogger(AlarmIntegrationConfig::class.java)

    @Bean
    fun fcmBatchTriggerChannel(): MessageChannel = DirectChannel()

    @Bean
    fun fcmInputChannel(): MessageChannel = DirectChannel()

    @Bean
    fun fcmProcessChannel(): MessageChannel = DirectChannel()

    @Bean
    fun fcmOutputChannel(): MessageChannel = PublishSubscribeChannel()

    @Bean
    fun fcmErrorChannel(): MessageChannel = PublishSubscribeChannel()

    @Bean
    fun fcmCompletionChannel(): MessageChannel = PublishSubscribeChannel()

    @Bean
    fun fcmBatchTriggerFlow(
        fcmBatchTriggerChannel: MessageChannel,
        fcmInputChannel: MessageChannel,
        fcmErrorChannel: MessageChannel,
    ): IntegrationFlow =
        integrationFlow(fcmBatchTriggerChannel) {
            handle<BatchFcmRequest> { request, headers ->
                try {
                    logger.info("FCM 배치 전송 시작 - 요청 ID: ${request.requestId}, 타입: ${request.notificationType}")

                    val users = request.users
                    if (users.isEmpty()) {
                        logger.warn("전송할 활성 FCM 토큰이 없습니다.")
                        return@handle emptyList<FcmNotificationMessage>()
                    }

                    logger.info("총 ${users.size}개의 활성 토큰에 알림 전송 시작")

                    users.map { user ->
                        FcmNotificationMessage(
                            user = user,
                            title = request.title,
                            notificationType = request.notificationType,
                        )
                    }
                } catch (e: Exception) {
                    logger.error("FCM 배치 전송 준비 중 오류 발생: ${request.requestId}", e)
                    fcmErrorChannel.send(
                        MessageBuilder
                            .withPayload(e)
                            .copyHeaders(headers)
                            .setHeader("requestId", request.requestId)
                            .setHeader("stage", "batch_preparation")
                            .build(),
                    )
                    emptyList<FcmNotificationMessage>()
                }
            }

            split()

            channel(fcmInputChannel)
        }

    @Bean
    fun fcmProcessFlow(
        fcmInputChannel: MessageChannel,
        fcmProcessChannel: MessageChannel,
        fcmErrorChannel: MessageChannel,
    ): IntegrationFlow =
        integrationFlow(fcmInputChannel) {
            handle<FcmNotificationMessage> { message, headers ->
                try {
                    logger.debug("FCM 메시지 처리 시작: ${message.user.fcmToken}")

                    val body = alarmMessageResolver.resolveTodayMessage(message.user.deviceToken)

                    val success =
                        pushNotificationService.sendNotification(
                            token = message.user.fcmToken,
                            title = message.title,
                            body = body,
                        )

                    val result =
                        FcmNotificationResult(
                            fcmToken = message.user.fcmToken,
                            success = success,
                            errorMessage = if (!success) "FCM 전송 실패" else null,
                        )

                    if (success) {
                        logger.debug("FCM 알림 전송 성공: ${message.user.fcmToken}")
                    } else {
                        logger.warn("FCM 알림 전송 실패: ${message.user.fcmToken}")
                    }

                    result
                } catch (e: Exception) {
                    logger.error("FCM 알림 전송 중 오류 발생: ${message.user.fcmToken}", e)

                    fcmErrorChannel.send(
                        MessageBuilder
                            .withPayload(e)
                            .copyHeaders(headers)
                            .setHeader("fcmToken", message.user.fcmToken)
                            .setHeader("stage", "notification_sending")
                            .build(),
                    )

                    FcmNotificationResult(
                        fcmToken = message.user.fcmToken,
                        success = false,
                        errorMessage = e.message ?: "알 수 없는 오류",
                    )
                }
            }

            // 결과 집계 채널로 라우팅
            channel(fcmProcessChannel)
        }

    @Bean
    fun fcmResultFlow(
        fcmProcessChannel: MessageChannel,
        fcmOutputChannel: MessageChannel,
    ): IntegrationFlow =
        integrationFlow(fcmProcessChannel) {
            handle<FcmNotificationResult> { result, headers ->
                logger.debug("FCM 전송 결과 처리: ${result.fcmToken} - 성공: ${result.success}")
                result
            }

            // 결과를 최종 출력 채널로 전달
            channel(fcmOutputChannel)
        }

    @Bean
    fun fcmCompletionFlow(fcmOutputChannel: MessageChannel): IntegrationFlow =
        integrationFlow(fcmOutputChannel) {
            handle<FcmNotificationResult> { result, headers ->
                // 전송 완료 로그 기록
                if (result.success) {
                    logger.info("FCM 알림 전송 성공 처리 완료: ${result.fcmToken}")
                } else {
                    logger.warn("FCM 알림 전송 실패 처리 완료: ${result.fcmToken} - ${result.errorMessage}")
                }
            }
        }

    @Bean
    fun fcmErrorHandlingFlow(fcmErrorChannel: MessageChannel): IntegrationFlow =
        integrationFlow(fcmErrorChannel) {
            handle<Exception> { exception, headers ->
                val fcmToken = headers["fcmToken"] ?: "알 수 없는 토큰"
                val stage = headers["stage"] ?: "unknown"
                val requestId = headers["requestId"]

                logger.error(
                    "FCM 처리 중 오류 발생 - 단계: $stage, 토큰: $fcmToken, 요청 ID: $requestId, 오류: ${exception.message}",
                    exception,
                )
            }
        }

    companion object {
        const val ALARM_TITLE = "오늘도 빠뜨리지 말고 쏙!"
    }
}
