package com.nexters.api.batch.service

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient
import com.google.analytics.data.v1beta.DimensionValue
import com.google.analytics.data.v1beta.MetricValue
import com.google.analytics.data.v1beta.Row
import com.google.analytics.data.v1beta.RunReportRequest
import com.google.analytics.data.v1beta.RunReportResponse
import com.nexters.api.batch.dto.ContentDetailPageView
import com.nexters.api.batch.dto.ContentDetailPageViewReport
import com.nexters.api.batch.dto.NewsletterClick
import com.nexters.api.batch.dto.NewsletterPageView
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleAnalyticsServiceTest {
    private val analyticsClient: BetaAnalyticsDataClient = Mockito.mock(BetaAnalyticsDataClient::class.java)

    private val sut = GoogleAnalyticsService(analyticsClient)

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(sut, "propertyId", "test-property")
    }

    @Test
    fun `getTopNewsletterClicksForRollingWindow should request rolling 365 day click report`() {
        val endDate = LocalDate.of(2026, 4, 18)
        val response =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("object-1").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("42").build())
                        .build(),
                ).build()

        Mockito
            .`when`(analyticsClient.runReport(any()))
            .thenReturn(response)

        val result = sut.getTopNewsletterClicksForRollingWindow(endDate)

        assertEquals(listOf(NewsletterClick(objectId = "object-1", clickCount = 42L)), result)

        val requestCaptor = ArgumentCaptor.forClass(RunReportRequest::class.java)
        Mockito.verify(analyticsClient).runReport(requestCaptor.capture())

        val request = requestCaptor.value
        assertEquals("properties/test-property", request.property)
        assertEquals("2025-04-19", request.dateRangesList.single().startDate)
        assertEquals("2026-04-18", request.dateRangesList.single().endDate)
        assertEquals("customEvent:object_id", request.dimensionsList.single().name)
        assertEquals("eventCount", request.metricsList.single().name)
        assertEquals("eventName", request.dimensionFilter.filter.fieldName)
        assertEquals("click_newsletter", request.dimensionFilter.filter.stringFilter.value)
        assertTrue(request.orderBysList.single().desc)
        assertEquals(
            "eventCount",
            request.orderBysList
                .single()
                .metric.metricName
        )
        assertEquals(20L, request.limit)
    }

    @Test
    fun `getDailyReport should include newsletter carousel page view report`() {
        val targetDate = LocalDate.of(2026, 6, 25)
        val todayUserStatsResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("new").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("10").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("12").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("30").build())
                        .build(),
                ).build()
        val yesterdayTotalUsersResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addMetricValues(MetricValue.newBuilder().setValue("20").build())
                        .build(),
                ).build()
        val newsletterClickResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("clicked-newsletter").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("4").build())
                        .build(),
                ).build()
        val carouselPageViewResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("viewed-newsletter").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("7").build())
                        .build(),
                ).build()
        val contentDetailPageViewCountResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("main_contents_detail_pageview").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("24").build())
                        .build(),
                ).addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("explore_contents_detail_pageview").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("19").build())
                        .build(),
                ).build()
        val mainContentDetailPageViewResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("main-content-title").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("11").build())
                        .build(),
                ).build()
        val exploreContentDetailPageViewResponse =
            RunReportResponse
                .newBuilder()
                .addRows(
                    Row
                        .newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("explore-content-title").build())
                        .addMetricValues(MetricValue.newBuilder().setValue("5").build())
                        .build(),
                ).build()

        Mockito
            .`when`(analyticsClient.runReport(any()))
            .thenReturn(
                todayUserStatsResponse,
                yesterdayTotalUsersResponse,
                newsletterClickResponse,
                carouselPageViewResponse,
                contentDetailPageViewCountResponse,
                mainContentDetailPageViewResponse,
                exploreContentDetailPageViewResponse,
            )

        val result = sut.getDailyReport(targetDate)

        assertEquals(
            listOf(NewsletterPageView(objectId = "viewed-newsletter", viewCount = 7L)),
            result.topNewsletterCarouselPageViews,
        )
        assertEquals(
            listOf(
                ContentDetailPageViewReport(
                    eventName = "main_contents_detail_pageview",
                    label = "메인 콘텐츠 상세 조회",
                    totalCount = 24L,
                    topContentTitles = listOf(ContentDetailPageView("main-content-title", 11L)),
                ),
                ContentDetailPageViewReport(
                    eventName = "explore_contents_detail_pageview",
                    label = "탐색 콘텐츠 상세 조회",
                    totalCount = 19L,
                    topContentTitles = listOf(ContentDetailPageView("explore-content-title", 5L)),
                ),
            ),
            result.contentDetailPageViewReports,
        )

        val requestCaptor = ArgumentCaptor.forClass(RunReportRequest::class.java)
        Mockito.verify(analyticsClient, Mockito.times(7)).runReport(requestCaptor.capture())

        val request = requestCaptor.allValues[3]
        assertEquals("properties/test-property", request.property)
        assertEquals("2026-06-25", request.dateRangesList.single().startDate)
        assertEquals("2026-06-25", request.dateRangesList.single().endDate)
        assertEquals("customEvent:object_id", request.dimensionsList.single().name)
        assertEquals("eventCount", request.metricsList.single().name)
        val eventNameFilter =
            request.dimensionFilter
                .andGroup
                .expressionsList[0]
                .filter
        val notSetObjectIdFilter =
            request.dimensionFilter
                .andGroup
                .expressionsList[1]
                .notExpression
                .filter
        val orderMetric =
            request.orderBysList
                .single()
                .metric

        assertEquals("pageview_newsletter_carousel", eventNameFilter.stringFilter.value)
        assertEquals(
            "customEvent:object_id",
            notSetObjectIdFilter.fieldName,
        )
        assertEquals(
            "(not set)",
            notSetObjectIdFilter.stringFilter.value,
        )
        assertTrue(request.orderBysList.single().desc)
        assertEquals("eventCount", orderMetric.metricName)
        assertEquals(5L, request.limit)

        val contentDetailCountRequest = requestCaptor.allValues[4]
        assertEquals("eventName", contentDetailCountRequest.dimensionsList.single().name)
        assertEquals("eventCount", contentDetailCountRequest.metricsList.single().name)
        val contentDetailEventNames =
            contentDetailCountRequest.dimensionFilter
                .orGroup
                .expressionsList
                .map { it.filter.stringFilter.value }
        assertEquals(
            listOf("main_contents_detail_pageview", "explore_contents_detail_pageview"),
            contentDetailEventNames,
        )

        val mainContentDetailRequest = requestCaptor.allValues[5]
        assertEquals("customEvent:content_title", mainContentDetailRequest.dimensionsList.single().name)
        assertEquals("eventCount", mainContentDetailRequest.metricsList.single().name)
        assertEquals(
            "main_contents_detail_pageview",
            mainContentDetailRequest.dimensionFilter
                .andGroup
                .expressionsList[0]
                .filter
                .stringFilter
                .value,
        )
        assertEquals(
            "customEvent:content_title",
            mainContentDetailRequest.dimensionFilter
                .andGroup
                .expressionsList[1]
                .notExpression
                .filter
                .fieldName,
        )
        assertEquals(5L, mainContentDetailRequest.limit)

        val exploreContentDetailRequest = requestCaptor.allValues[6]
        assertEquals("customEvent:content_title", exploreContentDetailRequest.dimensionsList.single().name)
        assertEquals(
            "explore_contents_detail_pageview",
            exploreContentDetailRequest.dimensionFilter
                .andGroup
                .expressionsList[0]
                .filter
                .stringFilter
                .value,
        )
        assertEquals(5L, exploreContentDetailRequest.limit)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T {
        Mockito.any<T>()
        return null as T
    }
}
