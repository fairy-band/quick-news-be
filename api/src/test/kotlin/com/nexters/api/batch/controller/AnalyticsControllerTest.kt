package com.nexters.api.batch.controller

import com.nexters.api.batch.service.DailyAnalyticsService
import com.nexters.api.batch.service.PopularNewsletterRankingBatchService
import com.nexters.api.batch.service.WeeklyAnalyticsService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@WebMvcTest(controllers = [AnalyticsController::class], properties = ["batch.enabled=true"])
@ActiveProfiles("test")
class AnalyticsControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var dailyAnalyticsService: DailyAnalyticsService

    @MockitoBean
    private lateinit var weeklyAnalyticsService: WeeklyAnalyticsService

    @MockitoBean
    private lateinit var popularNewsletterRankingBatchService: PopularNewsletterRankingBatchService

    @Test
    fun `sendDailyReport should return 500 when report generation fails`() {
        val date = LocalDate.of(2026, 4, 18)

        Mockito
            .`when`(dailyAnalyticsService.generateAndSendDailyReport(date))
            .thenReturn(false)

        mockMvc
            .perform(
                post("/api/analytics/report/send")
                    .param("date", date.toString()),
            ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("일일 리포트 전송에 실패했습니다."))
            .andExpect(jsonPath("$.date").value("2026-04-18"))
    }

    @Test
    fun `sendWeeklyReport should return 500 when exception occurs`() {
        val endDate = LocalDate.of(2026, 4, 18)

        Mockito
            .`when`(weeklyAnalyticsService.generateAndSendWeeklyReport(endDate))
            .thenThrow(IllegalStateException("weekly failed"))

        mockMvc
            .perform(
                post("/api/analytics/report/weekly/send")
                    .param("endDate", endDate.toString()),
            ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("주간 리포트 전송 중 오류가 발생했습니다: weekly failed"))
    }

    @Test
    fun `rebuildPopularNewsletters should return 500 when rebuild throws`() {
        val endDate = LocalDate.of(2026, 4, 19)

        Mockito
            .`when`(popularNewsletterRankingBatchService.rebuildGlobalRanking(endDate, 365, 20))
            .thenThrow(IllegalStateException("rebuild failed"))

        mockMvc
            .perform(
                post("/api/analytics/popular-newsletters/rebuild")
                    .param("endDate", endDate.toString()),
            ).andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("인기 뉴스레터 랭킹 스냅샷 재집계 중 오류가 발생했습니다: rebuild failed"))
    }
}
