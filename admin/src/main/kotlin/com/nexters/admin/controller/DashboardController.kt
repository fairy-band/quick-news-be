package com.nexters.admin.controller

import com.nexters.admin.dto.AIMetrics
import com.nexters.admin.dto.Activity
import com.nexters.admin.dto.ChartData
import com.nexters.admin.dto.DashboardMetrics
import com.nexters.admin.dto.Distribution
import com.nexters.admin.dto.ModelRateLimitStatus
import com.nexters.admin.dto.TimeRange
import com.nexters.admin.service.DashboardService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 대시보드 페이지 컨트롤러
 */
@Controller
@RequestMapping("/dashboard")
class DashboardController {
    private val logger = KotlinLogging.logger {}

    @GetMapping
    fun dashboard(): String {
        logger.info { "대시보드 페이지 요청" }
        return "dashboard"
    }
}

/**
 * 대시보드 API 컨트롤러
 */
@RestController
@RequestMapping("/api/dashboard")
class DashboardApiController(
    private val dashboardService: DashboardService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주요 지표 조회
     */
    @GetMapping("/metrics")
    fun getMetrics(
        @RequestParam(defaultValue = "ALL_TIME") timeRange: TimeRange
    ): ResponseEntity<DashboardMetrics> {
        logger.info { "주요 지표 조회 API 호출: timeRange=$timeRange" }
        val metrics = dashboardService.getMetrics(timeRange)
        return ResponseEntity.ok(metrics)
    }

    /**
     * 차트 데이터 조회
     */
    @GetMapping("/chart")
    fun getChartData(
        @RequestParam(defaultValue = "THIS_MONTH") timeRange: TimeRange
    ): ResponseEntity<ChartData> {
        logger.info { "차트 데이터 조회 API 호출: timeRange=$timeRange" }
        val chartData = dashboardService.getChartData(timeRange)
        return ResponseEntity.ok(chartData)
    }

    /**
     * 뉴스레터 소스별 분포 조회
     */
    @GetMapping("/distribution/newsletter")
    fun getNewsletterDistribution(
        @RequestParam(defaultValue = "ALL_TIME") timeRange: TimeRange
    ): ResponseEntity<List<Distribution>> {
        logger.info { "뉴스레터 분포 조회 API 호출: timeRange=$timeRange" }
        val distribution = dashboardService.getNewsletterDistribution(timeRange)
        return ResponseEntity.ok(distribution)
    }

    /**
     * 카테고리별 분포 조회
     */
    @GetMapping("/distribution/category")
    fun getCategoryDistribution(
        @RequestParam(defaultValue = "ALL_TIME") timeRange: TimeRange
    ): ResponseEntity<List<Distribution>> {
        logger.info { "카테고리 분포 조회 API 호출: timeRange=$timeRange" }
        val distribution = dashboardService.getCategoryDistribution(timeRange)
        return ResponseEntity.ok(distribution)
    }

    /**
     * 최근 활동 로그 조회
     */
    @GetMapping("/activities")
    fun getRecentActivities(
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<List<Activity>> {
        logger.info { "최근 활동 조회 API 호출: limit=$limit" }
        val activities = dashboardService.getRecentActivities(limit)
        return ResponseEntity.ok(activities)
    }

    /**
     * AI 성능 지표 조회
     */
    @GetMapping("/ai-metrics")
    fun getAIMetrics(
        @RequestParam(defaultValue = "ALL_TIME") timeRange: TimeRange
    ): ResponseEntity<AIMetrics> {
        logger.info { "AI 성능 지표 조회 API 호출: timeRange=$timeRange" }
        val aiMetrics = dashboardService.getAIMetrics(timeRange)
        return ResponseEntity.ok(aiMetrics)
    }

    /**
     * 모델별 오늘 Rate Limit 현황 조회
     */
    @GetMapping("/rate-limit")
    fun getModelRateLimitStatus(): ResponseEntity<List<ModelRateLimitStatus>> {
        logger.info { "모델별 Rate Limit 현황 조회 API 호출" }
        val rateLimitStatus = dashboardService.getModelRateLimitStatus()
        return ResponseEntity.ok(rateLimitStatus)
    }
}
