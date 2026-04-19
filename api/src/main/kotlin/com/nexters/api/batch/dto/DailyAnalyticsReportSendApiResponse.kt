package com.nexters.api.batch.dto

import java.time.LocalDate

data class DailyAnalyticsReportSendApiResponse(
    val success: Boolean,
    val message: String,
    val date: LocalDate? = null,
)
