package com.nexters.newsletterfeeder.config

import com.nexters.newsletterfeeder.service.DailyAnalyticsService
import com.nexters.newsletterfeeder.service.WeeklyAnalyticsService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
@Profile("prod", "dev")
class AnalyticsScheduler(
    private val dailyAnalyticsService: DailyAnalyticsService,
    private val weeklyAnalyticsService: WeeklyAnalyticsService
) {
    private val logger = LoggerFactory.getLogger(AnalyticsScheduler::class.java)

    /**
     * 매일 아침 8시에 전날 GA 통계를 디스코드로 전송
     * 크론 표현식: "0 0 8 * * *" = 매일 08:00:00
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    fun sendDailyAnalyticsReport() {
        logger.info("일일 GA 통계 리포트 스케줄 실행 시작")

        try {
            val success = dailyAnalyticsService.generateAndSendDailyReport()

            if (success) {
                logger.info("일일 GA 통계 리포트 스케줄 실행 완료")
            } else {
                logger.error("일일 GA 통계 리포트 전송 실패")
            }
        } catch (e: Exception) {
            logger.error("일일 GA 통계 리포트 스케줄 실행 중 오류 발생", e)
        }
    }

    /**
     * 매주 월요일 아침 9시에 지난 주 GA 통계를 디스코드로 전송
     * 크론 표현식: "0 0 9 * * MON" = 매주 월요일 09:00:00
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    fun sendWeeklyAnalyticsReport() {
        logger.info("주간 GA 통계 리포트 스케줄 실행 시작")

        try {
            val success = weeklyAnalyticsService.generateAndSendWeeklyReport()

            if (success) {
                logger.info("주간 GA 통계 리포트 스케줄 실행 완료")
            } else {
                logger.error("주간 GA 통계 리포트 전송 실패")
            }
        } catch (e: Exception) {
            logger.error("주간 GA 통계 리포트 스케줄 실행 중 오류 발생", e)
        }
    }
}
