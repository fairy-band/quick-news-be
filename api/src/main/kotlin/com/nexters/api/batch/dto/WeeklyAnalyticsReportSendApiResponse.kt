package com.nexters.api.batch.dto

import java.time.LocalDate

data class WeeklyAnalyticsReportSendApiResponse(
    val success: Boolean,
    val message: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)
