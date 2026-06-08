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

    @Test
    fun `calculate should expose rule results while preserving existing score formula`() {
        val today = LocalDate.of(2026, 6, 8)
        val calculator =
            RecommendScoreCalculator(
                rules =
                    listOf(
                        KeywordAffinityRule(),
                        RerankingBonusRule(),
                        FreshnessRule { today },
                        DuplicatePublisherPenaltyRule(),
                    ),
            )
        val source =
            RecommendCalculateSource(
                positiveKeywordSources =
                    listOf(
                        PositiveKeywordSource(weight = 2.0, keywordName = "Kotlin"),
                        PositiveKeywordSource(weight = 3.0, keywordName = "Spring"),
                    ),
                negativeKeywordSources = listOf(NegativeKeywordSource(weight = -2.0, keywordName = "iOS")),
                publishedDate = today.minusDays(1),
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = 10.0,
                rerankingBonus = 5.0,
                rerankingRuleResults =
                    listOf(
                        RecommendationRuleResult(
                            ruleName = "ai_llm_signal",
                            score = 5.0,
                            reason = "matched=true",
                            type = RecommendationRuleType.BONUS,
                        ),
                    ),
            )

        val result = calculator.calculate(source)

        assertThat(result.recommendScore).isEqualTo(9.0)
        assertThat(result.ruleResults.map { it.ruleName })
            .containsExactly(
                "keyword_affinity",
                "reranking_bonus",
                "freshness",
                "duplicate_publisher_penalty",
            )
    }

    @Test
    fun `calculate should include floor adjustment when raw score is negative`() {
        val today = LocalDate.of(2026, 6, 8)
        val calculator =
            RecommendScoreCalculator(
                rules =
                    listOf(
                        KeywordAffinityRule(),
                        RerankingBonusRule(),
                        FreshnessRule { today },
                        DuplicatePublisherPenaltyRule(),
                    ),
            )
        val source =
            RecommendCalculateSource(
                positiveKeywordSources = emptyList(),
                negativeKeywordSources = listOf(NegativeKeywordSource(weight = -10.0, keywordName = "Android")),
                publishedDate = today.minusDays(10),
                publisherDuplicateCandidateCount = 1,
            )

        val result = calculator.calculate(source)

        assertThat(result.recommendScore).isZero()
        assertThat(result.ruleResults.last().ruleName).isEqualTo("final_score_floor")
        assertThat(result.ruleResults.last().score).isGreaterThan(0.0)
    }
}
