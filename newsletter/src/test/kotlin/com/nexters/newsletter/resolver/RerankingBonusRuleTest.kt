package com.nexters.newsletter.resolver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RerankingBonusRuleTest {
    private val today = LocalDate.of(2026, 6, 8)
    private val rule = RerankingBonusRule { today }

    @Test
    fun `evaluate should boost recent new AI platform signals`() {
        val results =
            rule.evaluate(
                newAiPlatformSource(
                    publishedDate = today,
                ),
            )
        val bonus = results.sumOf { it.score }

        assertThat(bonus).isGreaterThanOrEqualTo(180.0)
    }

    @Test
    fun `evaluate should expose reranking signal rules`() {
        val result =
            rule.evaluate(
                newAiPlatformSource(
                    publishedDate = today,
                ),
            )

        assertThat(result.map { it.ruleName })
            .contains(
                "new_technology_signal",
                "ai_llm_signal",
                "platform_signal",
                "signal_synergy",
                "reranking_recency",
            )
    }

    @Test
    fun `evaluate should decay recency bonus for stale generic content`() {
        val staleGenericResults =
            rule.evaluate(
                genericSource(
                    publishedDate = today.minusDays(45),
                ),
            )
        val staleGenericBonus = staleGenericResults.sumOf { it.score }

        val recentGenericResults =
            rule.evaluate(
                genericSource(
                    publishedDate = today,
                ),
            )
        val recentGenericBonus = recentGenericResults.sumOf { it.score }

        assertThat(staleGenericBonus).isZero()
        assertThat(recentGenericBonus).isEqualTo(60.0)
    }

    private fun newAiPlatformSource(publishedDate: LocalDate): RecommendCalculateSource =
        RecommendCalculateSource(
            title = "OpenAI launches a new Agents SDK for MCP platforms",
            provocativeHeadline = "AI agents get a new platform API",
            summaryContent = "The release introduces a framework for building LLM applications.",
            newsletterName = "Web Tools Weekly",
            contentProviderName = "Web Tools Weekly",
            keywordNames = listOf("AI", "LLM", "Platform"),
            positiveKeywordSources = emptyList(),
            negativeKeywordSources = emptyList(),
            publishedDate = publishedDate,
            publisherDuplicateCandidateCount = 0,
        )

    private fun genericSource(publishedDate: LocalDate): RecommendCalculateSource =
        RecommendCalculateSource(
            title = "Refactoring tips for teams",
            provocativeHeadline = "Clean code habits",
            summaryContent = "A general article about team practices.",
            newsletterName = "General Newsletter",
            contentProviderName = null,
            keywordNames = emptyList(),
            positiveKeywordSources = emptyList(),
            negativeKeywordSources = emptyList(),
            publishedDate = publishedDate,
            publisherDuplicateCandidateCount = 0,
        )
}
