package com.nexters.api.batch.service

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient
import com.google.analytics.data.v1beta.DimensionValue
import com.google.analytics.data.v1beta.MetricValue
import com.google.analytics.data.v1beta.Row
import com.google.analytics.data.v1beta.RunReportRequest
import com.google.analytics.data.v1beta.RunReportResponse
import com.nexters.api.batch.dto.NewsletterClick
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T {
        Mockito.any<T>()
        return null as T
    }
}
