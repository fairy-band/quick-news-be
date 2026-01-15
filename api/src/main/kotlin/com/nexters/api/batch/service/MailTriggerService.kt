package com.nexters.api.batch.service

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
class MailTriggerService(
    private val scheduleTriggerChannel: MessageChannel,
    private val mailInputChannel: MessageChannel
) {
    private val logger = LoggerFactory.getLogger(MailTriggerService::class.java)

    /**
     * 매일 아침 8시에 메일 읽기 작업 트리거
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // 5분마다 실행
    fun triggerDailyMailReading() {
        logger.info("메일 읽기 시작")

        scheduleTriggerChannel.send(
            MessageBuilder
                .withPayload("DAILY_TRIGGER")
                .setHeader("triggerType", "SCHEDULED")
                .build()
        )
    }

    /**
     * 수동으로 메일 읽기 작업 직접 트리거 (API 호출 등에서 사용)
     */
    fun triggerManualMailReading() {
        logger.info("수동 메일 읽기 시작")

        mailInputChannel.send(
            MessageBuilder
                .withPayload("MANUAL_TRIGGER")
                .setHeader("triggerType", "MANUAL")
                .build()
        )
    }
}
