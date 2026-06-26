package com.nexters.api.batch.service

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient
import com.google.analytics.data.v1beta.DateRange
import com.google.analytics.data.v1beta.Dimension
import com.google.analytics.data.v1beta.Filter
import com.google.analytics.data.v1beta.FilterExpression
import com.google.analytics.data.v1beta.FilterExpressionList
import com.google.analytics.data.v1beta.Metric
import com.google.analytics.data.v1beta.OrderBy
import com.google.analytics.data.v1beta.RunReportRequest
import com.nexters.api.batch.dto.AnalyticsReport
import com.nexters.api.batch.dto.ContentDetailPageView
import com.nexters.api.batch.dto.ContentDetailPageViewReport
import com.nexters.api.batch.dto.NewsletterClick
import com.nexters.api.batch.dto.NewsletterPageView
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class GoogleAnalyticsService(
    private val analyticsClient: BetaAnalyticsDataClient
) {
    private val logger = LoggerFactory.getLogger(GoogleAnalyticsService::class.java)
    private val contentDetailPageViewEvents =
        listOf(
            ContentDetailPageViewEvent(
                eventName = MAIN_CONTENT_DETAIL_PAGEVIEW_EVENT_NAME,
                label = "메인 콘텐츠 상세 조회",
            ),
            ContentDetailPageViewEvent(
                eventName = EXPLORE_CONTENT_DETAIL_PAGEVIEW_EVENT_NAME,
                label = "탐색 콘텐츠 상세 조회",
            ),
        )

    @Value("\${google.analytics.property.id}")
    private lateinit var propertyId: String

    fun getDailyReport(date: LocalDate = LocalDate.now().minusDays(1)): AnalyticsReport {
        logger.info("GA 일일 리포트 생성 시작: $date")

        try {
            val dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val yesterdayString = date.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // 오늘 사용자 통계 요청
            val todayUserStatsRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(dateString)
                            .setEndDate(dateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName("newVsReturning")
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("totalUsers")
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("sessions")
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("screenPageViews")
                            .build()
                    ).build()

            val todayResponse = analyticsClient.runReport(todayUserStatsRequest)

            var newUsers = 0L
            var returningUsers = 0L
            var sessions = 0L
            var pageViews = 0L

            // 오늘 데이터 파싱
            todayResponse.rowsList.forEach { row ->
                val userType = row.getDimensionValues(0).value
                val users = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                val sessionCount = row.getMetricValues(1).value.toLongOrNull() ?: 0L
                val pageViewCount = row.getMetricValues(2).value.toLongOrNull() ?: 0L

                sessions += sessionCount
                pageViews += pageViewCount

                when (userType) {
                    "new" -> newUsers = users
                    "returning" -> returningUsers = users
                }
            }

            // 어제 총 방문자 수 조회
            val yesterdayTotalUsers = getYesterdayTotalUsers(yesterdayString)

            // 총 사용자는 신규 + 재방문 사용자의 합
            val totalUsers = newUsers + returningUsers

            // 재방문율 = (어제 방문한 유저 대비 오늘 재방문한 유저 수)
            val returningUserRate =
                if (yesterdayTotalUsers > 0) {
                    (returningUsers.toDouble() / yesterdayTotalUsers.toDouble()) * 100
                } else {
                    0.0
                }

            // Newsletter 클릭 데이터 조회
            val topNewsletterClicks = getTopNewsletterClicks(dateString)
            val topNewsletterCarouselPageViews = getTopNewsletterCarouselPageViews(dateString)
            val contentDetailPageViewReports =
                getContentDetailPageViewReports(
                    startDateString = dateString,
                    endDateString = dateString,
                    limit = 5,
                )

            val report =
                AnalyticsReport(
                    date = date,
                    totalUsers = totalUsers,
                    newUsers = newUsers,
                    returningUsers = returningUsers,
                    returningUserRate = returningUserRate,
                    sessions = sessions,
                    pageViews = pageViews,
                    topNewsletterClicks = topNewsletterClicks,
                    topNewsletterCarouselPageViews = topNewsletterCarouselPageViews,
                    contentDetailPageViewReports = contentDetailPageViewReports,
                    yesterdayTotalUsers = yesterdayTotalUsers
                )

            logger.info(
                "GA 일일 리포트 생성 완료: 총 사용자 ${totalUsers}명, 어제 방문자 ${yesterdayTotalUsers}명, 재방문율 ${String.format("%.1f", returningUserRate)}%"
            )
            return report
        } catch (e: Exception) {
            logger.error("GA 일일 리포트 생성 중 오류 발생", e)
            throw RuntimeException("Google Analytics 데이터 조회 실패", e)
        }
    }

    fun getTopNewsletterClicksForRollingWindow(
        endDate: LocalDate,
        lookbackDays: Int = 365,
        limit: Int = 20,
    ): List<NewsletterClick> {
        val windowStartDate = endDate.minusDays(lookbackDays.toLong() - 1)
        val startDateString = windowStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val endDateString = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        return getTopNewsletterClicksForPeriod(startDateString, endDateString, limit)
    }

    private fun getYesterdayTotalUsers(yesterdayString: String): Long =
        try {
            logger.info("어제 총 방문자 수 조회 시작: $yesterdayString")

            val yesterdayUserRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(yesterdayString)
                            .setEndDate(yesterdayString)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("totalUsers")
                            .build()
                    ).build()

            val response = analyticsClient.runReport(yesterdayUserRequest)
            val yesterdayTotalUsers =
                response.rowsList
                    .firstOrNull()
                    ?.getMetricValues(0)
                    ?.value
                    ?.toLongOrNull() ?: 0L

            logger.info("어제 총 방문자 수 조회 완료: ${yesterdayTotalUsers}명")
            yesterdayTotalUsers
        } catch (e: Exception) {
            logger.error("어제 총 방문자 수 조회 중 오류 발생", e)
            0L
        }

    private fun getLastWeekTotalUsers(
        lastWeekStartDate: LocalDate,
        lastWeekEndDate: LocalDate
    ): Long =
        try {
            val startDateString = lastWeekStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val endDateString = lastWeekEndDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            logger.info("지난 주 총 방문자 수 조회 시작: $startDateString ~ $endDateString")

            val lastWeekUserRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(startDateString)
                            .setEndDate(endDateString)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("totalUsers")
                            .build()
                    ).build()

            val response = analyticsClient.runReport(lastWeekUserRequest)
            val lastWeekTotalUsers =
                response.rowsList
                    .firstOrNull()
                    ?.getMetricValues(0)
                    ?.value
                    ?.toLongOrNull() ?: 0L

            logger.info("지난 주 총 방문자 수 조회 완료: ${lastWeekTotalUsers}명")
            lastWeekTotalUsers
        } catch (e: Exception) {
            logger.error("지난 주 총 방문자 수 조회 중 오류 발생", e)
            0L
        }

    private fun getTopNewsletterClicks(dateString: String): List<NewsletterClick> =
        try {
            logger.info("Newsletter 클릭 데이터 조회 시작")

            val newsletterClickRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(dateString)
                            .setEndDate(dateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName(OBJECT_ID_DIMENSION)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("eventCount")
                            .build()
                    ).setDimensionFilter(
                        FilterExpression
                            .newBuilder()
                            .setFilter(
                                Filter
                                    .newBuilder()
                                    .setFieldName(EVENT_NAME_DIMENSION)
                                    .setStringFilter(
                                        Filter.StringFilter
                                            .newBuilder()
                                            .setValue(NEWSLETTER_CLICK_EVENT_NAME)
                                            .setMatchType(Filter.StringFilter.MatchType.EXACT)
                                            .build()
                                    ).build()
                            ).build()
                    ).addOrderBys(
                        OrderBy
                            .newBuilder()
                            .setMetric(
                                OrderBy.MetricOrderBy
                                    .newBuilder()
                                    .setMetricName("eventCount")
                                    .build()
                            ).setDesc(true)
                            .build()
                    ).setLimit(5)
                    .build()

            val response = analyticsClient.runReport(newsletterClickRequest)

            val newsletterClicks =
                response.rowsList.map { row ->
                    val objectId = row.getDimensionValues(0).value
                    val clickCount = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                    NewsletterClick(objectId, clickCount)
                }

            logger.info("Newsletter 클릭 데이터 조회 완료: ${newsletterClicks.size}개 항목")
            newsletterClicks
        } catch (e: Exception) {
            logger.error("Newsletter 클릭 데이터 조회 중 오류 발생", e)
            emptyList()
        }

    private fun getTopNewsletterCarouselPageViews(dateString: String): List<NewsletterPageView> =
        getTopNewsletterCarouselPageViewsForPeriod(
            startDateString = dateString,
            endDateString = dateString,
            limit = 5,
        )

    fun getWeeklyReport(
        startDate: LocalDate,
        endDate: LocalDate
    ): AnalyticsReport {
        logger.info("GA 주간 리포트 생성 시작: $startDate ~ $endDate")

        try {
            val startDateString = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val endDateString = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // 이번 주 사용자 통계 요청
            val thisWeekUserStatsRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(startDateString)
                            .setEndDate(endDateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName("newVsReturning")
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("totalUsers")
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("sessions")
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("screenPageViews")
                            .build()
                    ).build()

            val thisWeekResponse = analyticsClient.runReport(thisWeekUserStatsRequest)

            var newUsers = 0L
            var returningUsers = 0L
            var sessions = 0L
            var pageViews = 0L

            // 이번 주 데이터 파싱
            thisWeekResponse.rowsList.forEach { row ->
                val userType = row.getDimensionValues(0).value
                val users = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                val sessionCount = row.getMetricValues(1).value.toLongOrNull() ?: 0L
                val pageViewCount = row.getMetricValues(2).value.toLongOrNull() ?: 0L

                sessions += sessionCount
                pageViews += pageViewCount

                when (userType) {
                    "new" -> newUsers = users
                    "returning" -> returningUsers = users
                }
            }

            // 지난 주 총 방문자 수 조회 (7일 전 ~ 1일 전)
            val lastWeekStartDate = startDate.minusDays(7)
            val lastWeekEndDate = startDate.minusDays(1)
            val lastWeekTotalUsers = getLastWeekTotalUsers(lastWeekStartDate, lastWeekEndDate)

            // 총 사용자는 신규 + 재방문 사용자의 합
            val totalUsers = newUsers + returningUsers

            // 재방문율 = (지난 주 방문한 유저 대비 이번 주 재방문한 유저 수)
            val returningUserRate =
                if (lastWeekTotalUsers > 0) {
                    (returningUsers.toDouble() / lastWeekTotalUsers.toDouble()) * 100
                } else {
                    0.0
                }

            // Newsletter 클릭 데이터 조회
            val topNewsletterClicks = getTopNewsletterClicksForPeriod(startDateString, endDateString)
            val topNewsletterCarouselPageViews = getTopNewsletterCarouselPageViewsForPeriod(startDateString, endDateString)
            val contentDetailPageViewReports =
                getContentDetailPageViewReports(
                    startDateString = startDateString,
                    endDateString = endDateString,
                    limit = 10,
                )

            val report =
                AnalyticsReport(
                    date = endDate, // 주간 리포트의 경우 종료일을 대표 날짜로 사용
                    totalUsers = totalUsers,
                    newUsers = newUsers,
                    returningUsers = returningUsers,
                    returningUserRate = returningUserRate,
                    sessions = sessions,
                    pageViews = pageViews,
                    topNewsletterClicks = topNewsletterClicks,
                    topNewsletterCarouselPageViews = topNewsletterCarouselPageViews,
                    contentDetailPageViewReports = contentDetailPageViewReports,
                    startDate = startDate,
                    endDate = endDate,
                    yesterdayTotalUsers = lastWeekTotalUsers
                )

            logger.info(
                "GA 주간 리포트 생성 완료: 총 사용자 ${totalUsers}명, 지난 주 방문자 ${lastWeekTotalUsers}명, 재방문율 ${String.format("%.1f", returningUserRate)}%"
            )
            return report
        } catch (e: Exception) {
            logger.error("GA 주간 리포트 생성 중 오류 발생", e)
            throw RuntimeException("Google Analytics 주간 데이터 조회 실패", e)
        }
    }

    private fun getTopNewsletterClicksForPeriod(
        startDateString: String,
        endDateString: String,
        limit: Int = 10,
    ): List<NewsletterClick> =
        try {
            logger.info("Newsletter 클릭 데이터 조회 시작 ($startDateString ~ $endDateString)")

            val newsletterClickRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(startDateString)
                            .setEndDate(endDateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName(OBJECT_ID_DIMENSION)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName("eventCount")
                            .build()
                    ).setDimensionFilter(
                        FilterExpression
                            .newBuilder()
                            .setFilter(
                                Filter
                                    .newBuilder()
                                    .setFieldName(EVENT_NAME_DIMENSION)
                                    .setStringFilter(
                                        Filter.StringFilter
                                            .newBuilder()
                                            .setValue(NEWSLETTER_CLICK_EVENT_NAME)
                                            .setMatchType(Filter.StringFilter.MatchType.EXACT)
                                            .build()
                                    ).build()
                            ).build()
                    ).addOrderBys(
                        OrderBy
                            .newBuilder()
                            .setMetric(
                                OrderBy.MetricOrderBy
                                    .newBuilder()
                                    .setMetricName("eventCount")
                                    .build()
                            ).setDesc(true)
                            .build()
                    ).setLimit(limit.toLong())
                    .build()

            val response = analyticsClient.runReport(newsletterClickRequest)

            val newsletterClicks =
                response.rowsList.map { row ->
                    val objectId = row.getDimensionValues(0).value
                    val clickCount = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                    NewsletterClick(objectId, clickCount)
                }

            logger.info("Newsletter 클릭 데이터 조회 완료: ${newsletterClicks.size}개 항목")
            newsletterClicks
        } catch (e: Exception) {
            logger.error("Newsletter 클릭 데이터 조회 중 오류 발생", e)
            emptyList()
        }

    private fun getTopNewsletterCarouselPageViewsForPeriod(
        startDateString: String,
        endDateString: String,
        limit: Int = 10,
    ): List<NewsletterPageView> =
        try {
            logger.info("Newsletter 캐러셀 조회 데이터 조회 시작 ($startDateString ~ $endDateString)")

            val newsletterCarouselPageViewRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(startDateString)
                            .setEndDate(endDateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName(OBJECT_ID_DIMENSION)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName(EVENT_COUNT_METRIC)
                            .build()
                    ).setDimensionFilter(
                        FilterExpression
                            .newBuilder()
                            .setAndGroup(
                                FilterExpressionList
                                    .newBuilder()
                                    .addExpressions(exactEventNameFilter(NEWSLETTER_CAROUSEL_PAGEVIEW_EVENT_NAME))
                                    .addExpressions(notSetObjectIdFilter())
                                    .build()
                            ).build()
                    ).addOrderBys(
                        OrderBy
                            .newBuilder()
                            .setMetric(
                                OrderBy.MetricOrderBy
                                    .newBuilder()
                                    .setMetricName(EVENT_COUNT_METRIC)
                                    .build()
                            ).setDesc(true)
                            .build()
                    ).setLimit(limit.toLong())
                    .build()

            val response = analyticsClient.runReport(newsletterCarouselPageViewRequest)

            val newsletterPageViews =
                response.rowsList.map { row ->
                    val objectId = row.getDimensionValues(0).value
                    val viewCount = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                    NewsletterPageView(objectId, viewCount)
                }

            logger.info("Newsletter 캐러셀 조회 데이터 조회 완료: ${newsletterPageViews.size}개 항목")
            newsletterPageViews
        } catch (e: Exception) {
            logger.error("Newsletter 캐러셀 조회 데이터 조회 중 오류 발생", e)
            emptyList()
        }

    private fun getContentDetailPageViewReports(
        startDateString: String,
        endDateString: String,
        limit: Int,
    ): List<ContentDetailPageViewReport> {
        val countsByEventName = getContentDetailPageViewCounts(startDateString, endDateString)

        return contentDetailPageViewEvents.map { event ->
            ContentDetailPageViewReport(
                eventName = event.eventName,
                label = event.label,
                totalCount = countsByEventName[event.eventName] ?: 0L,
                topContentTitles =
                    getTopContentDetailPageViewsForEvent(
                        startDateString = startDateString,
                        endDateString = endDateString,
                        eventName = event.eventName,
                        limit = limit,
                    ),
            )
        }
    }

    private fun getContentDetailPageViewCounts(
        startDateString: String,
        endDateString: String,
    ): Map<String, Long> =
        try {
            logger.info("콘텐츠 상세 조회 이벤트 수 조회 시작 ($startDateString ~ $endDateString)")

            val contentDetailPageViewCountRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(startDateString)
                            .setEndDate(endDateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName(EVENT_NAME_DIMENSION)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName(EVENT_COUNT_METRIC)
                            .build()
                    ).setDimensionFilter(
                        eventNameAnyFilter(contentDetailPageViewEvents.map { it.eventName })
                    ).build()

            val response = analyticsClient.runReport(contentDetailPageViewCountRequest)
            val contentDetailPageViewCounts =
                response.rowsList.associate { row ->
                    val eventName = row.getDimensionValues(0).value
                    val viewCount = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                    eventName to viewCount
                }

            logger.info("콘텐츠 상세 조회 이벤트 수 조회 완료: $contentDetailPageViewCounts")
            contentDetailPageViewCounts
        } catch (e: Exception) {
            logger.error("콘텐츠 상세 조회 이벤트 수 조회 중 오류 발생", e)
            emptyMap()
        }

    private fun getTopContentDetailPageViewsForEvent(
        startDateString: String,
        endDateString: String,
        eventName: String,
        limit: Int,
    ): List<ContentDetailPageView> =
        try {
            logger.info("콘텐츠 상세 조회 TOP 데이터 조회 시작 eventName=$eventName ($startDateString ~ $endDateString)")

            val contentDetailPageViewRequest =
                RunReportRequest
                    .newBuilder()
                    .setProperty("properties/$propertyId")
                    .addDateRanges(
                        DateRange
                            .newBuilder()
                            .setStartDate(startDateString)
                            .setEndDate(endDateString)
                            .build()
                    ).addDimensions(
                        Dimension
                            .newBuilder()
                            .setName(CONTENT_TITLE_DIMENSION)
                            .build()
                    ).addMetrics(
                        Metric
                            .newBuilder()
                            .setName(EVENT_COUNT_METRIC)
                            .build()
                    ).setDimensionFilter(
                        FilterExpression
                            .newBuilder()
                            .setAndGroup(
                                FilterExpressionList
                                    .newBuilder()
                                    .addExpressions(exactEventNameFilter(eventName))
                                    .addExpressions(notSetDimensionFilter(CONTENT_TITLE_DIMENSION))
                                    .build()
                            ).build()
                    ).addOrderBys(
                        OrderBy
                            .newBuilder()
                            .setMetric(
                                OrderBy.MetricOrderBy
                                    .newBuilder()
                                    .setMetricName(EVENT_COUNT_METRIC)
                                    .build()
                            ).setDesc(true)
                            .build()
                    ).setLimit(limit.toLong())
                    .build()

            val response = analyticsClient.runReport(contentDetailPageViewRequest)
            val contentDetailPageViews =
                response.rowsList.map { row ->
                    val contentTitle = row.getDimensionValues(0).value
                    val viewCount = row.getMetricValues(0).value.toLongOrNull() ?: 0L
                    ContentDetailPageView(contentTitle, viewCount)
                }

            logger.info("콘텐츠 상세 조회 TOP 데이터 조회 완료 eventName=$eventName: ${contentDetailPageViews.size}개 항목")
            contentDetailPageViews
        } catch (e: Exception) {
            logger.error("콘텐츠 상세 조회 TOP 데이터 조회 중 오류 발생 eventName=$eventName", e)
            emptyList()
        }

    private fun exactEventNameFilter(eventName: String): FilterExpression =
        FilterExpression
            .newBuilder()
            .setFilter(
                Filter
                    .newBuilder()
                    .setFieldName(EVENT_NAME_DIMENSION)
                    .setStringFilter(
                        Filter.StringFilter
                            .newBuilder()
                            .setValue(eventName)
                            .setMatchType(Filter.StringFilter.MatchType.EXACT)
                            .build()
                    ).build()
            ).build()

    private fun eventNameAnyFilter(eventNames: List<String>): FilterExpression =
        FilterExpression
            .newBuilder()
            .setOrGroup(
                FilterExpressionList
                    .newBuilder()
                    .addAllExpressions(eventNames.map(::exactEventNameFilter))
                    .build()
            ).build()

    private fun notSetObjectIdFilter(): FilterExpression = notSetDimensionFilter(OBJECT_ID_DIMENSION)

    private fun notSetDimensionFilter(dimensionName: String): FilterExpression =
        FilterExpression
            .newBuilder()
            .setNotExpression(
                FilterExpression
                    .newBuilder()
                    .setFilter(
                        Filter
                            .newBuilder()
                            .setFieldName(dimensionName)
                            .setStringFilter(
                                Filter.StringFilter
                                    .newBuilder()
                                    .setValue(NOT_SET_VALUE)
                                    .setMatchType(Filter.StringFilter.MatchType.EXACT)
                                    .build()
                            ).build()
                    ).build()
            ).build()

    companion object {
        private const val EVENT_NAME_DIMENSION = "eventName"
        private const val EVENT_COUNT_METRIC = "eventCount"
        private const val OBJECT_ID_DIMENSION = "customEvent:object_id"
        private const val CONTENT_TITLE_DIMENSION = "customEvent:content_title"
        private const val NEWSLETTER_CLICK_EVENT_NAME = "click_newsletter"
        private const val NEWSLETTER_CAROUSEL_PAGEVIEW_EVENT_NAME = "pageview_newsletter_carousel"
        private const val MAIN_CONTENT_DETAIL_PAGEVIEW_EVENT_NAME = "main_contents_detail_pageview"
        private const val EXPLORE_CONTENT_DETAIL_PAGEVIEW_EVENT_NAME = "explore_contents_detail_pageview"
        private const val NOT_SET_VALUE = "(not set)"
    }
}

private data class ContentDetailPageViewEvent(
    val eventName: String,
    val label: String,
)
