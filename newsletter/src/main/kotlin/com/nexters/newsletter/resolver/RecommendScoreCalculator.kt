package com.nexters.newsletter.resolver

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RecommendScoreCalculator {
    fun calculate(source: RecommendCalculateSource): RecommendCalculateResult {
        // 양수 가중치의 곱 계산
        val positiveWeight =
            source.positiveKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight
            }

        // 음수 가중치의 곱 계산
        val negativeWeight =
            source.negativeKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight * -1 // 음수이므로 -1을 곱함
            }

        // 최종 가중치 = 양수 가중치의 곱 + 음수 가중치의 합
        // 음수 가중치가 너무 크면 0으로 만들기 위해 max 사용
        val keywordScore = maxOf(positiveWeight - negativeWeight, 0.0)

        val today = LocalDate.now()
        val daysDifference = ChronoUnit.DAYS.between(source.publishedDate, today)
        val freshScore = -daysDifference * 10 // 달수, 연수를 포함한 총 날짜차이를 음수로 계산
        val lastScore = maxOf(keywordScore + freshScore, 0.0)

        return RecommendCalculateResult(lastScore)
    }
}

data class RecommendCalculateResult(
    val recommendScore: Double,
)

data class RecommendCalculateSource(
    val positiveKeywordSources: List<PositiveKeywordSource>,
    val negativeKeywordSources: List<NegativeKeywordSource>,
    val publishedDate: LocalDate,
)

data class PositiveKeywordSource(
    val weight: Double,
)

data class NegativeKeywordSource(
    val weight: Double,
)
