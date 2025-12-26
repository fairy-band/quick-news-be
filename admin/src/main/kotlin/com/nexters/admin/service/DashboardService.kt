package com.nexters.admin.service

import com.nexters.admin.dto.AIMetrics
import com.nexters.admin.dto.Activity
import com.nexters.admin.dto.ActivityType
import com.nexters.admin.dto.ChartData
import com.nexters.admin.dto.DashboardMetrics
import com.nexters.admin.dto.Distribution
import com.nexters.admin.dto.TimeRange
import com.nexters.admin.repository.DashboardContentRepository
import com.nexters.admin.repository.DashboardExposureContentRepository
import com.nexters.admin.repository.DashboardKeywordRepository
import com.nexters.admin.repository.DashboardSummaryRepository
import com.nexters.external.repository.NewsletterSourceRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * 대시보드 데이터를 제공하는 서비스
 */
@Service
class DashboardService(
    private val dashboardContentRepository: DashboardContentRepository,
    private val dashboardExposureContentRepository: DashboardExposureContentRepository,
    private val dashboardSummaryRepository: DashboardSummaryRepository,
    private val dashboardKeywordRepository: DashboardKeywordRepository,
    private val newsletterSourceRepository: NewsletterSourceRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * 주요 지표 조회
     */
    fun getMetrics(timeRange: TimeRange): DashboardMetrics {
        logger.info { "대시보드 지표 조회 시작: timeRange=$timeRange" }

        val dateRange = calculateDateRange(timeRange)

        val totalContents =
            if (dateRange != null) {
                dashboardContentRepository.countByCreatedAtBetween(dateRange.first, dateRange.second)
            } else {
                dashboardContentRepository.count()
            }

        val todayStart = LocalDateTime.now().toLocalDate().atStartOfDay()
        val todayEnd = LocalDateTime.now()
        val todayContents = dashboardContentRepository.countByCreatedAtBetween(todayStart, todayEnd)

        // 요약 있는/없는 콘텐츠 수 계산 (N+1 방지)
        val allContents =
            if (dateRange != null) {
                dashboardContentRepository.findAllByCreatedAtBetween(dateRange.first, dateRange.second)
            } else {
                dashboardContentRepository.findAll()
            }

        // N+1 방지: 한 번의 쿼리로 요약이 있는 Content ID 조회
        val contentIds = allContents.mapNotNull { it.id }
        val contentIdsWithSummary = if (contentIds.isNotEmpty()) {
            dashboardSummaryRepository.findContentIdsWithSummary(contentIds).toSet()
        } else {
            emptySet()
        }

        val contentsWithSummary = contentIdsWithSummary.size.toLong()
        val contentsWithoutSummary = allContents.size - contentsWithSummary

        val exposedContents =
            if (dateRange != null) {
                dashboardExposureContentRepository.countByCreatedAtBetween(dateRange.first, dateRange.second)
            } else {
                dashboardExposureContentRepository.count()
            }

        val totalKeywords = dashboardKeywordRepository.count()
        val activeNewsletterSources = newsletterSourceRepository.count()

        logger.info { "대시보드 지표 조회 완료: totalContents=$totalContents" }

        return DashboardMetrics(
            totalContents = totalContents,
            todayContents = todayContents,
            contentsWithSummary = contentsWithSummary,
            contentsWithoutSummary = contentsWithoutSummary,
            exposedContents = exposedContents,
            totalKeywords = totalKeywords,
            activeNewsletterSources = activeNewsletterSources
        )
    }

    /**
     * 차트 데이터 조회
     */
    fun getChartData(timeRange: TimeRange): ChartData {
        logger.info { "차트 데이터 조회 시작: timeRange=$timeRange" }

        val dateRange = calculateDateRange(timeRange)
        val startDate = dateRange?.first?.toLocalDate() ?: LocalDate.now().minusMonths(1)
        val endDate = dateRange?.second?.toLocalDate() ?: LocalDate.now()

        val labels = mutableListOf<String>()
        val processedData = mutableListOf<Int>()
        val unprocessedData = mutableListOf<Int>()

        // 전체 기간의 콘텐츠를 한 번에 조회
        val allContents = dashboardContentRepository.findAllByCreatedAtBetween(
            startDate.atStartOfDay(),
            endDate.plusDays(1).atStartOfDay()
        )

        // N+1 방지: 한 번의 쿼리로 요약이 있는 Content ID 조회
        val contentIds = allContents.mapNotNull { it.id }
        val contentIdsWithSummary = if (contentIds.isNotEmpty()) {
            dashboardSummaryRepository.findContentIdsWithSummary(contentIds).toSet()
        } else {
            emptySet()
        }

        // 날짜별로 그룹화
        val contentsByDate = allContents.groupBy { it.createdAt.toLocalDate() }

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            labels.add(currentDate.toString())

            val dayContents = contentsByDate[currentDate] ?: emptyList()

            var processed = 0
            var unprocessed = 0

            dayContents.forEach { content ->
                if (content.id in contentIdsWithSummary) {
                    processed++
                } else {
                    unprocessed++
                }
            }

            processedData.add(processed)
            unprocessedData.add(unprocessed)

            currentDate = currentDate.plusDays(1)
        }

        logger.info { "차트 데이터 조회 완료: ${labels.size}일치 데이터" }

        return ChartData(
            labels = labels,
            processedData = processedData,
            unprocessedData = unprocessedData
        )
    }

    /**
     * 뉴스레터 소스별 분포 조회
     */
    fun getNewsletterDistribution(timeRange: TimeRange): List<Distribution> {
        logger.info { "뉴스레터 분포 조회 시작: timeRange=$timeRange" }

        val dateRange = calculateDateRange(timeRange)

        val distributionData =
            if (dateRange != null) {
                dashboardContentRepository.countByNewsletterNameBetween(dateRange.first, dateRange.second)
            } else {
                dashboardContentRepository.countByNewsletterName()
            }

        val totalCount = distributionData.sumOf { it.count }.toDouble()

        val distributions =
            distributionData
                .take(10)
                .map { data ->
                    Distribution(
                        name = data.name,
                        count = data.count,
                        percentage = if (totalCount > 0) (data.count / totalCount) * 100 else 0.0
                    )
                }

        logger.info { "뉴스레터 분포 조회 완료: ${distributions.size}개 소스" }

        return distributions
    }

    /**
     * 카테고리별 분포 조회
     */
    fun getCategoryDistribution(timeRange: TimeRange): List<Distribution> {
        logger.info { "카테고리 분포 조회 시작: timeRange=$timeRange" }

        val dateRange = calculateDateRange(timeRange)

        val distributionData =
            if (dateRange != null) {
                dashboardContentRepository.countByCategoryBetween(dateRange.first, dateRange.second)
            } else {
                dashboardContentRepository.countByCategory()
            }

        val totalCount = distributionData.sumOf { it.count }.toDouble()

        val distributions =
            distributionData.map { data ->
                Distribution(
                    name = data.name,
                    count = data.count,
                    percentage = if (totalCount > 0) (data.count / totalCount) * 100 else 0.0
                )
            }

        logger.info { "카테고리 분포 조회 완료: ${distributions.size}개 카테고리" }

        return distributions
    }

    /**
     * 최근 활동 로그 조회
     */
    fun getRecentActivities(limit: Int = 20): List<Activity> {
        logger.info { "최근 활동 조회 시작: limit=$limit" }

        val activities = mutableListOf<Activity>()

        // 최근 생성된 콘텐츠
        val recentContents =
            dashboardContentRepository
                .findRecentContents()
                .take(limit)

        recentContents.forEach { content ->
            activities.add(
                Activity(
                    id = content.id ?: 0,
                    type = ActivityType.CONTENT_CREATED,
                    contentTitle = content.title,
                    timestamp = content.createdAt,
                    details = "뉴스레터: ${content.newsletterName}"
                )
            )
        }

        // 시간순 정렬 (최신순)
        val sortedActivities = activities.sortedByDescending { it.timestamp }.take(limit)

        logger.info { "최근 활동 조회 완료: ${sortedActivities.size}개 활동" }

        return sortedActivities
    }

    /**
     * AI 성능 지표 조회
     */
    fun getAIMetrics(timeRange: TimeRange): AIMetrics {
        logger.info { "AI 성능 지표 조회 시작: timeRange=$timeRange" }

        val dateRange = calculateDateRange(timeRange)
        val contents =
            if (dateRange != null) {
                dashboardContentRepository.findAllByCreatedAtBetween(dateRange.first, dateRange.second)
            } else {
                dashboardContentRepository.findAll()
            }

        // N+1 방지: 한 번의 쿼리로 요약이 있는 Content ID 조회
        val contentIds = contents.mapNotNull { it.id }
        val contentIdsWithSummary = if (contentIds.isNotEmpty()) {
            dashboardSummaryRepository.findContentIdsWithSummary(contentIds).toSet()
        } else {
            emptySet()
        }

        val totalAttempts = contents.size.toLong()
        val successCount = contentIdsWithSummary.size.toLong()

        val successRate =
            if (totalAttempts > 0) {
                (successCount.toDouble() / totalAttempts) * 100
            } else {
                0.0
            }

        val failureCount = totalAttempts - successCount

        logger.info { "AI 성능 지표 조회 완료: successRate=$successRate%" }

        return AIMetrics(
            successRate = successRate,
            averageProcessingTime = 0.0, // TODO: 실제 처리 시간 추적 필요
            totalApiCalls = totalAttempts,
            failureCount = failureCount
        )
    }

    /**
     * 시간 범위에 따른 날짜 범위 계산
     */
    private fun calculateDateRange(timeRange: TimeRange): Pair<LocalDateTime, LocalDateTime>? {
        val now = LocalDateTime.now()

        return when (timeRange) {
            TimeRange.TODAY -> {
                val startOfDay = now.toLocalDate().atStartOfDay()
                Pair(startOfDay, now)
            }
            TimeRange.THIS_WEEK -> {
                val startOfWeek =
                    now
                        .toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                        .atStartOfDay()
                Pair(startOfWeek, now)
            }
            TimeRange.THIS_MONTH -> {
                val startOfMonth =
                    now
                        .toLocalDate()
                        .with(TemporalAdjusters.firstDayOfMonth())
                        .atStartOfDay()
                Pair(startOfMonth, now)
            }
            TimeRange.ALL_TIME -> null
        }
    }
}
