package com.nexters.external.filter

import com.nexters.external.entity.Content
import java.time.LocalDate

/**
 * Content 날짜 기반 필터
 * - 특정 날짜 이후의 content만 허용
 */
class DateFilter(
    private val minDaysOld: Int
) : ContentFilter {
    override fun filter(content: Content): FilterResult {
        val publishedDate = content.publishedAt
        val cutoffDate = LocalDate.now().minusDays(minDaysOld.toLong())

        return when {
            publishedDate.isBefore(cutoffDate) -> {
                FilterResult.Fail("Content too old: published on $publishedDate (cutoff: $cutoffDate)")
            }
            else -> FilterResult.Pass
        }
    }

    override fun getName(): String = "DateFilter(within ${minDaysOld}days)"
}
