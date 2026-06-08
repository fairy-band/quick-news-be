package com.nexters.newsletter.resolver

import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface RecommendationRule {
    val name: String

    fun evaluate(source: RecommendCalculateSource): RecommendationRuleResult
}

data class RecommendationRuleResult(
    val ruleName: String,
    val score: Double,
    val reason: String,
    val type: RecommendationRuleType = RecommendationRuleType.SCORE,
)

enum class RecommendationRuleType {
    SCORE,
    BONUS,
    PENALTY,
    ADJUSTMENT,
}

class KeywordAffinityRule : RecommendationRule {
    override val name: String = "keyword_affinity"

    override fun evaluate(source: RecommendCalculateSource): RecommendationRuleResult {
        val positiveWeight =
            source.positiveKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight
            }
        val negativeWeight =
            source.negativeKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight * -1
            }
        val score = maxOf(positiveWeight - negativeWeight + source.categoryMatchBonus, 0.0)

        return RecommendationRuleResult(
            ruleName = name,
            score = score,
            reason =
                "positive=$positiveWeight, negative=$negativeWeight, categoryBonus=${source.categoryMatchBonus}, " +
                    "positiveKeywords=${source.positiveKeywordSources.keywordNames()}, " +
                    "negativeKeywords=${source.negativeKeywordSources.keywordNames()}",
        )
    }
}

class RerankingBonusRule : RecommendationRule {
    override val name: String = "reranking_bonus"

    override fun evaluate(source: RecommendCalculateSource): RecommendationRuleResult =
        RecommendationRuleResult(
            ruleName = name,
            score = source.rerankingBonus,
            reason = source.rerankingRuleResults.joinToString("; ") { "${it.ruleName}=${it.score}" }.ifBlank { "none" },
            type = RecommendationRuleType.BONUS,
        )
}

class FreshnessRule(
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : RecommendationRule {
    override val name: String = "freshness"

    override fun evaluate(source: RecommendCalculateSource): RecommendationRuleResult {
        val daysOld = ChronoUnit.DAYS.between(source.publishedDate, todayProvider())
        val score = -daysOld * FRESHNESS_DECAY_PER_DAY

        return RecommendationRuleResult(
            ruleName = name,
            score = score,
            reason = "publishedDate=${source.publishedDate}, daysOld=$daysOld",
            type = if (score < 0) RecommendationRuleType.PENALTY else RecommendationRuleType.BONUS,
        )
    }

    companion object {
        private const val FRESHNESS_DECAY_PER_DAY = 10.0
    }
}

class DuplicatePublisherPenaltyRule : RecommendationRule {
    override val name: String = "duplicate_publisher_penalty"

    override fun evaluate(source: RecommendCalculateSource): RecommendationRuleResult {
        val score = -source.publisherDuplicateCandidateCount * DUPLICATE_PUBLISHER_PENALTY

        return RecommendationRuleResult(
            ruleName = name,
            score = score,
            reason = "publisherDuplicateCandidateCount=${source.publisherDuplicateCandidateCount}",
            type = RecommendationRuleType.PENALTY,
        )
    }

    companion object {
        private const val DUPLICATE_PUBLISHER_PENALTY = 50.0
    }
}

class FinalScoreFloorRule : RecommendationRule {
    override val name: String = "final_score_floor"

    override fun evaluate(source: RecommendCalculateSource): RecommendationRuleResult =
        RecommendationRuleResult(
            ruleName = name,
            score = 0.0,
            reason = "applied by calculator when total score is below 0",
            type = RecommendationRuleType.ADJUSTMENT,
        )
}

private fun List<KeywordSource>.keywordNames(): String =
    mapNotNull { it.keywordName }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(",")
        ?: size.toString()
