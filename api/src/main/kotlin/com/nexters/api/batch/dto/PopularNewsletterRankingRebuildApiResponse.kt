package com.nexters.api.batch.dto

import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import java.time.LocalDate

data class PopularNewsletterRankingRebuildApiResponse(
    val success: Boolean,
    val message: String,
    val snapshotId: Long? = null,
    val status: PopularNewsletterSnapshotStatus? = null,
    val resolvedItemCount: Int? = null,
    val windowStartDate: LocalDate? = null,
    val windowEndDate: LocalDate? = null,
)
