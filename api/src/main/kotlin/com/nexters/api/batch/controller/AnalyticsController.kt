package com.nexters.api.batch.controller

import com.nexters.api.batch.dto.DailyAnalyticsReportSendApiResponse
import com.nexters.api.batch.dto.PopularNewsletterRankingRebuildApiResponse
import com.nexters.api.batch.dto.WeeklyAnalyticsReportSendApiResponse
import com.nexters.api.batch.service.DailyAnalyticsService
import com.nexters.api.batch.service.PopularNewsletterRankingBatchService
import com.nexters.api.batch.service.WeeklyAnalyticsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/analytics")
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AnalyticsController(
    private val dailyAnalyticsService: DailyAnalyticsService,
    private val weeklyAnalyticsService: WeeklyAnalyticsService,
    private val popularNewsletterRankingBatchService: PopularNewsletterRankingBatchService,
) {
    private val logger = LoggerFactory.getLogger(AnalyticsController::class.java)

    @PostMapping("/report/send")
    fun sendDailyReport(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<DailyAnalyticsReportSendApiResponse> =
        try {
            val targetDate = date ?: LocalDate.now().minusDays(1)
            logger.info("수동 일일 리포트 전송 요청: $targetDate")

            val success = dailyAnalyticsService.generateAndSendDailyReport(targetDate)

            if (success) {
                ResponseEntity.ok(
                    DailyAnalyticsReportSendApiResponse(
                        success = true,
                        message = "일일 리포트가 성공적으로 전송되었습니다.",
                        date = targetDate,
                    ),
                )
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    DailyAnalyticsReportSendApiResponse(
                        success = false,
                        message = "일일 리포트 전송에 실패했습니다.",
                        date = targetDate,
                    ),
                )
            }
        } catch (e: Exception) {
            logger.error("수동 일일 리포트 전송 중 오류 발생", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                DailyAnalyticsReportSendApiResponse(
                    success = false,
                    message = "리포트 전송 중 오류가 발생했습니다: ${e.message}",
                ),
            )
        }

    @PostMapping("/report/weekly/send")
    fun sendWeeklyReport(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?
    ): ResponseEntity<WeeklyAnalyticsReportSendApiResponse> =
        try {
            val targetEndDate = endDate ?: LocalDate.now().minusDays(1)
            val targetStartDate = targetEndDate.minusDays(6)
            logger.info("수동 주간 리포트 전송 요청: $targetStartDate ~ $targetEndDate")

            val success = weeklyAnalyticsService.generateAndSendWeeklyReport(targetEndDate)

            if (success) {
                ResponseEntity.ok(
                    WeeklyAnalyticsReportSendApiResponse(
                        success = true,
                        message = "주간 리포트가 성공적으로 전송되었습니다.",
                        startDate = targetStartDate,
                        endDate = targetEndDate,
                    ),
                )
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    WeeklyAnalyticsReportSendApiResponse(
                        success = false,
                        message = "주간 리포트 전송에 실패했습니다.",
                        startDate = targetStartDate,
                        endDate = targetEndDate,
                    ),
                )
            }
        } catch (e: Exception) {
            logger.error("수동 주간 리포트 전송 중 오류 발생", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                WeeklyAnalyticsReportSendApiResponse(
                    success = false,
                    message = "주간 리포트 전송 중 오류가 발생했습니다: ${e.message}",
                ),
            )
        }

    @PostMapping("/popular-newsletters/rebuild")
    fun rebuildPopularNewsletters(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?,
        @RequestParam(defaultValue = "365")
        lookbackDays: Int,
        @RequestParam(defaultValue = "20")
        limit: Int,
    ): ResponseEntity<PopularNewsletterRankingRebuildApiResponse> =
        try {
            val targetEndDate = endDate ?: LocalDate.now()
            logger.info("인기 뉴스레터 랭킹 스냅샷 수동 재집계 요청: endDate={}, lookbackDays={}, limit={}", targetEndDate, lookbackDays, limit)

            val snapshot = popularNewsletterRankingBatchService.rebuildGlobalRanking(targetEndDate, lookbackDays, limit)

            ResponseEntity.ok(
                PopularNewsletterRankingRebuildApiResponse(
                    success = true,
                    message = "인기 뉴스레터 랭킹 스냅샷 재집계를 완료했습니다.",
                    snapshotId = snapshot.id,
                    status = snapshot.status,
                    resolvedItemCount = snapshot.resolvedItemCount,
                    windowStartDate = snapshot.windowStartDate,
                    windowEndDate = snapshot.windowEndDate,
                ),
            )
        } catch (e: Exception) {
            logger.error("인기 뉴스레터 랭킹 스냅샷 수동 재집계 중 오류 발생", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                PopularNewsletterRankingRebuildApiResponse(
                    success = false,
                    message = "인기 뉴스레터 랭킹 스냅샷 재집계 중 오류가 발생했습니다: ${e.message}",
                ),
            )
        }
}
