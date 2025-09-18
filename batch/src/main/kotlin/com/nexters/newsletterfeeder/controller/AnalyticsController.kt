package com.nexters.newsletterfeeder.controller

import com.nexters.newsletterfeeder.service.DailyAnalyticsService
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController(
    private val dailyAnalyticsService: DailyAnalyticsService
) {
    private val logger = LoggerFactory.getLogger(AnalyticsController::class.java)

    @PostMapping("/report/send")
    fun sendDailyReport(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<Map<String, Any>> =
        try {
            val targetDate = date ?: LocalDate.now().minusDays(1)
            logger.info("수동 일일 리포트 전송 요청: $targetDate")

            val success = dailyAnalyticsService.generateAndSendDailyReport(targetDate)

            if (success) {
                ResponseEntity.ok(
                    mapOf(
                        "success" to true,
                        "message" to "일일 리포트가 성공적으로 전송되었습니다.",
                        "date" to targetDate.toString()
                    )
                )
            } else {
                ResponseEntity.ok(
                    mapOf(
                        "success" to false,
                        "message" to "일일 리포트 전송에 실패했습니다.",
                        "date" to targetDate.toString()
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("수동 일일 리포트 전송 중 오류 발생", e)
            ResponseEntity.ok(
                mapOf(
                    "success" to false,
                    "message" to "리포트 전송 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
}
