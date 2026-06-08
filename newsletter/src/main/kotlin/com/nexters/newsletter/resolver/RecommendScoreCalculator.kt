package com.nexters.newsletter.resolver

import java.time.LocalDate

class RecommendScoreCalculator(
    private val rules: List<RecommendationRule> =
        listOf(
            KeywordAffinityRule(),
            RerankingBonusRule(),
            FreshnessRule(),
            DuplicatePublisherPenaltyRule(),
        ),
    private val finalScoreFloorRule: RecommendationRule = FinalScoreFloorRule(),
) {
    fun calculate(source: RecommendCalculateSource): RecommendCalculateResult {
        val ruleResults = rules.map { rule -> rule.evaluate(source) }
        val rawScore = ruleResults.sumOf { it.score }
        val recommendScore = maxOf(rawScore, 0.0)
        val finalRuleResults =
            if (recommendScore == rawScore) {
                ruleResults
            } else {
                ruleResults + finalScoreFloorRule.evaluate(source).copy(score = -rawScore)
            }

        return RecommendCalculateResult(
            recommendScore = recommendScore,
            ruleResults = finalRuleResults,
        )
    }
}

data class RecommendCalculateResult(
    val recommendScore: Double,
    val ruleResults: List<RecommendationRuleResult> = emptyList(),
)

data class RecommendCalculateSource(
    val positiveKeywordSources: List<PositiveKeywordSource>,
    val negativeKeywordSources: List<NegativeKeywordSource>,
    val publishedDate: LocalDate,
    val publisherDuplicateCandidateCount: Int,
    val categoryMatchBonus: Double = 0.0,
    val rerankingBonus: Double = 0.0,
    val rerankingRuleResults: List<RecommendationRuleResult> = emptyList(),
)

interface KeywordSource {
    val weight: Double
    val keywordName: String?
}

data class PositiveKeywordSource(
    override val weight: Double,
    override val keywordName: String? = null,
) : KeywordSource

data class NegativeKeywordSource(
    override val weight: Double,
    override val keywordName: String? = null,
) : KeywordSource
