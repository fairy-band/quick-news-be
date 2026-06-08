package com.nexters.newsletter.resolver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecommendScoreCalculatorTest {
    private val calculator = RecommendScoreCalculator()

    @Test
    fun `calculate should add composite reranking signal bonuses`() {
        val today = LocalDate.of(2026, 6, 8)
        val calculator =
            RecommendScoreCalculator(
                rules =
                    listOf(
                        KeywordAffinityRule(),
                        RerankingBonusRule { today },
                        FreshnessRule { today },
                        DuplicatePublisherPenaltyRule(),
                    ),
            )
        val source =
            RecommendCalculateSource(
                title = "OpenAI launches a new Agents SDK for MCP platforms",
                provocativeHeadline = "AI agents get a new platform API",
                summaryContent = "The release introduces a framework for building LLM applications.",
                newsletterName = "Web Tools Weekly",
                contentProviderName = "Web Tools Weekly",
                keywordNames = listOf("AI", "LLM", "Platform"),
                positiveKeywordSources = listOf(PositiveKeywordSource(100.0)),
                negativeKeywordSources = emptyList(),
                publishedDate = today,
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = 0.0,
            )

        val result = calculator.calculate(source)

        assertThat(result.recommendScore).isGreaterThanOrEqualTo(280.0)
        assertThat(result.ruleResults.map { it.ruleName })
            .contains(
                "new_technology_signal",
                "ai_llm_signal",
                "platform_signal",
                "signal_synergy",
                "reranking_recency",
            )
    }

    @Test
    fun `calculate should expose rule results while preserving existing score formula`() {
        val today = LocalDate.of(2026, 6, 8)
        val calculator =
            RecommendScoreCalculator(
                rules =
                    listOf(
                        KeywordAffinityRule(),
                        RerankingBonusRule { today },
                        FreshnessRule { today },
                        DuplicatePublisherPenaltyRule(),
                    ),
            )
        val source =
            RecommendCalculateSource(
                title = "OpenAI launches a new Agents SDK for MCP platforms",
                provocativeHeadline = "AI agents get a new platform API",
                summaryContent = "The release introduces a framework for building LLM applications.",
                newsletterName = "Web Tools Weekly",
                contentProviderName = "Web Tools Weekly",
                keywordNames = listOf("AI", "LLM", "Platform"),
                positiveKeywordSources =
                    listOf(
                        PositiveKeywordSource(weight = 2.0, keywordName = "Kotlin"),
                        PositiveKeywordSource(weight = 3.0, keywordName = "Spring"),
                    ),
                negativeKeywordSources = listOf(NegativeKeywordSource(weight = -2.0, keywordName = "iOS")),
                publishedDate = today.minusDays(1),
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = 10.0,
            )

        val result = calculator.calculate(source)

        assertThat(result.recommendScore).isEqualTo(194.0)
        assertThat(result.ruleResults.map { it.ruleName })
            .containsExactly(
                "keyword_affinity",
                "new_technology_signal",
                "ai_llm_signal",
                "platform_signal",
                "signal_synergy",
                "reranking_recency",
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
                        RerankingBonusRule { today },
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
