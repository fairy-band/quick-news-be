package com.nexters.newsletterfeeder.service

import com.nexters.external.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DailyAnalyticsService(
    private val googleAnalyticsService: GoogleAnalyticsService,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(DailyAnalyticsService::class.java)

    fun generateAndSendDailyReport(targetDate: LocalDate = LocalDate.now().minusDays(1)): Boolean =
        try {
            logger.info("일일 분석 리포트 생성 및 전송 시작: $targetDate")

            val analyticsReport = googleAnalyticsService.getDailyReport(targetDate)

            val discordMessage = analyticsReport.toDiscordMessage()

            val success = notificationService.sendAnalyticsReport(discordMessage)

            if (success) {
                logger.info(
                    "일일 분석 리포트 전송 완료: 총 사용자 ${analyticsReport.totalUsers}명, 재방문율 ${String.format(
                        "%.1f",
                        analyticsReport.returningUserRate
                    )}%"
                )
            } else {
                logger.error("일일 분석 리포트 전송 실패")
            }

            success
        } catch (e: Exception) {
            logger.error("일일 분석 리포트 생성 및 전송 중 오류 발생", e)
            false
        }
}
