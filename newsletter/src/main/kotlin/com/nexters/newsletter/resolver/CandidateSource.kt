package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import java.time.LocalDate

interface CandidateSource {
    val name: String
    val order: Int
    val defaultLimit: Int

    fun fetch(request: CandidateSourceRequest): List<CandidateSeed>
}

data class CandidateSourceRequest(
    val userId: Long,
    val categoryIds: List<Long>,
    val publishedFrom: LocalDate,
    val limit: Int,
    val window: CandidateRecencyWindow,
)

data class CandidateSeed(
    val candidate: ExposureContentRecommendationCandidateRow,
    val signals: List<CandidateSourceSignal>,
)

data class CandidatePoolItem(
    val candidate: ExposureContentRecommendationCandidateRow,
    val signals: List<CandidateSourceSignal>,
)

data class CandidatePool(
    val candidates: List<CandidatePoolItem>,
    val expandedWindow: CandidateRecencyWindow,
)

data class CandidateSourceSignal(
    val source: String,
    val score: Double,
    val confidence: Double,
    val reason: String,
)

enum class CandidateRecencyWindow(
    private val days: Long?,
    val limitMultiplier: Int,
) {
    DAYS_30(30, 1),
    DAYS_90(90, 2),
    DAYS_365(365, 3),
    ALL(null, 4),
    ;

    fun publishedFrom(today: LocalDate): LocalDate = days?.let { today.minusDays(it) } ?: MIN_PUBLISHED_DATE

    companion object {
        private val MIN_PUBLISHED_DATE = LocalDate.of(1970, 1, 1)
    }
}
