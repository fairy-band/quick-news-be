package com.nexters.admin.dto

import java.time.LocalDateTime

/**
 * 대시보드 주요 지표 DTO
 */
data class DashboardMetrics(
    val totalContents: Long,
    val todayContents: Long,
    val contentsWithSummary: Long,
    val contentsWithoutSummary: Long,
    val exposedContents: Long,
    val totalKeywords: Long,
    val activeNewsletterSources: Long
)

/**
 * 차트 데이터 DTO
 */
data class ChartData(
    // 날짜 레이블
    val labels: List<String>,
    // 처리된 콘텐츠 수
    val processedData: List<Int>,
    // 미처리 콘텐츠 수
    val unprocessedData: List<Int>
)

/**
 * 분포 데이터 DTO
 */
data class Distribution(
    // 뉴스레터명 또는 카테고리명
    val name: String,
    val count: Long,
    val percentage: Double
)

/**
 * 활동 로그 DTO
 */
data class Activity(
    val id: Long,
    val type: ActivityType,
    val contentTitle: String,
    val timestamp: LocalDateTime,
    val details: String?
)

/**
 * 활동 타입 enum
 */
enum class ActivityType {
    CONTENT_CREATED,
    SUMMARY_GENERATED,
    KEYWORD_MAPPED,
    CONTENT_EXPOSED
}

/**
 * AI 성능 지표 DTO
 */
data class AIMetrics(
    // 성공률 (%)
    val successRate: Double,
    // 평균 처리 시간 (초)
    val averageProcessingTime: Double,
    val totalApiCalls: Long,
    val failureCount: Long
)

/**
 * 시간 범위 enum
 */
enum class TimeRange {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    ALL_TIME
}
