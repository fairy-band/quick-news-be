package com.nexters.newsletter.resolver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecommendScoreCalculatorTest {
    private val calculator = RecommendScoreCalculator()

    @Test
    fun `calculate should add reranking bonus separately from keyword score`() {
        val baseline =
            RecommendCalculateSource(
                positiveKeywordSources = listOf(PositiveKeywordSource(100.0)),
                negativeKeywordSources = emptyList(),
                publishedDate = LocalDate.now(),
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = 0.0,
                rerankingBonus = 0.0,
            )
        val boosted = baseline.copy(rerankingBonus = 50.0)

        assertThat(calculator.calculate(boosted).recommendScore)
            .isEqualTo(calculator.calculate(baseline).recommendScore + 50.0)
    }
}
