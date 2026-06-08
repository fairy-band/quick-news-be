package com.nexters.newsletter.resolver

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RerankingBonusCalculatorTest {
    private val calculator = RerankingBonusCalculator()

    @Test
    fun `calculate should boost recent new AI platform signals`() {
        val bonus =
            calculator.calculate(
                RerankingBonusSource(
                    title = "OpenAI launches a new Agents SDK for MCP platforms",
                    provocativeHeadline = "AI agents get a new platform API",
                    summaryContent = "The release introduces a framework for building LLM applications.",
                    newsletterName = "Web Tools Weekly",
                    contentProviderName = "Web Tools Weekly",
                    keywordNames = listOf("AI", "LLM", "Platform"),
                    publishedDate = LocalDate.now(),
                ),
            )

        assertThat(bonus).isGreaterThanOrEqualTo(180.0)
    }

    @Test
    fun `calculate should decay recency bonus for stale generic content`() {
        val staleGenericBonus =
            calculator.calculate(
                RerankingBonusSource(
                    title = "Refactoring tips for teams",
                    provocativeHeadline = "Clean code habits",
                    summaryContent = "A general article about team practices.",
                    newsletterName = "General Newsletter",
                    contentProviderName = null,
                    keywordNames = emptyList(),
                    publishedDate = LocalDate.now().minusDays(45),
                ),
            )

        val recentGenericBonus =
            calculator.calculate(
                RerankingBonusSource(
                    title = "Refactoring tips for teams",
                    provocativeHeadline = "Clean code habits",
                    summaryContent = "A general article about team practices.",
                    newsletterName = "General Newsletter",
                    contentProviderName = null,
                    keywordNames = emptyList(),
                    publishedDate = LocalDate.now(),
                ),
            )

        assertThat(staleGenericBonus).isZero()
        assertThat(recentGenericBonus).isEqualTo(60.0)
    }
}
