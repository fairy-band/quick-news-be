package com.nexters.api.batch.service

import com.nexters.external.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@Profile("prod")
class WeeklyAnalyticsService(
    private val googleAnalyticsService: GoogleAnalyticsService,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(WeeklyAnalyticsService::class.java)

    fun generateAndSendWeeklyReport(endDate: LocalDate = LocalDate.now().minusDays(1)): Boolean =
        try {
            val startDate = endDate.minusDays(6) // 7일간의 데이터
            logger.info("주간 분석 리포트 생성 및 전송 시작: $startDate ~ $endDate")

            val analyticsReport = googleAnalyticsService.getWeeklyReport(startDate, endDate)

            val discordMessage = analyticsReport.toWeeklyDiscordMessage()

            val success = notificationService.sendAnalyticsReport(discordMessage)

            if (success) {
                logger.info(
                    "주간 분석 리포트 전송 완료: 총 사용자 ${analyticsReport.totalUsers}명, 재방문율 ${String.format(
                        "%.1f",
                        analyticsReport.returningUserRate
                    )}%"
                )
            } else {
                logger.error("주간 분석 리포트 전송 실패")
            }

            success
        } catch (e: Exception) {
            logger.error("주간 분석 리포트 생성 및 전송 중 오류 발생", e)
            false
        }
}
