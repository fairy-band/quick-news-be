package com.nexters.newsletter.resolver

import java.time.LocalDate
import java.time.temporal.ChronoUnit

interface RecommendationRule {
    val name: String

    fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult>

    fun score(source: RecommendCalculateSource): Double = evaluate(source).sumOf { it.score }
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

    override fun score(source: RecommendCalculateSource): Double = calculateScore(source)

    override fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult> {
        val positiveWeight =
            source.positiveKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight
            }
        val negativeWeight =
            source.negativeKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight * -1
            }
        val score = maxOf(positiveWeight - negativeWeight + source.categoryMatchBonus, 0.0)

        return listOf(
            RecommendationRuleResult(
                ruleName = name,
                score = score,
                reason =
                    "positive=$positiveWeight, negative=$negativeWeight, categoryBonus=${source.categoryMatchBonus}, " +
                        "positiveKeywords=${source.positiveKeywordSources.keywordNames()}, " +
                        "negativeKeywords=${source.negativeKeywordSources.keywordNames()}",
            ),
        )
    }

    private fun calculateScore(source: RecommendCalculateSource): Double {
        val positiveWeight =
            source.positiveKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight
            }
        val negativeWeight =
            source.negativeKeywordSources.fold(1.0) { acc, keyword ->
                acc * keyword.weight * -1
            }

        return maxOf(positiveWeight - negativeWeight + source.categoryMatchBonus, 0.0)
    }
}

class CandidateSourceSignalRule : RecommendationRule {
    override val name: String = "candidate_source_signal"

    override fun score(source: RecommendCalculateSource): Double = calculateScore(source)

    override fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult> {
        if (source.candidateSignals.isEmpty()) {
            return emptyList()
        }

        val sourceCount =
            source.candidateSignals
                .map { it.source }
                .distinct()
                .size
        val sourceQualityBonus =
            source.candidateSignals.sumOf { signal ->
                signal.score * signal.confidence * SOURCE_QUALITY_WEIGHT
            }
        val consensusBonus = (sourceCount - 1).coerceAtLeast(0) * CONSENSUS_BONUS_PER_ADDITIONAL_SOURCE
        val score = sourceQualityBonus + consensusBonus

        if (score == 0.0) {
            return emptyList()
        }

        return listOf(
            RecommendationRuleResult(
                ruleName = name,
                score = score,
                reason =
                    "sourceCount=$sourceCount, sourceQualityBonus=$sourceQualityBonus, " +
                        "consensusBonus=$consensusBonus",
                type = RecommendationRuleType.BONUS,
            ),
        )
    }

    companion object {
        private const val SOURCE_QUALITY_WEIGHT = 20.0
        private const val CONSENSUS_BONUS_PER_ADDITIONAL_SOURCE = 15.0
    }

    private fun calculateScore(source: RecommendCalculateSource): Double {
        if (source.candidateSignals.isEmpty()) {
            return 0.0
        }

        val sourceCount =
            source.candidateSignals
                .map { it.source }
                .distinct()
                .size
        val sourceQualityBonus =
            source.candidateSignals.sumOf { signal ->
                signal.score * signal.confidence * SOURCE_QUALITY_WEIGHT
            }
        val consensusBonus = (sourceCount - 1).coerceAtLeast(0) * CONSENSUS_BONUS_PER_ADDITIONAL_SOURCE

        return sourceQualityBonus + consensusBonus
    }
}

class RerankingBonusRule(
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : RecommendationRule {
    override val name: String = "reranking_bonus"

    override fun score(source: RecommendCalculateSource): Double = calculateScore(source).totalScore

    override fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult> {
        val score = calculateScore(source)

        return listOf(
            RecommendationRuleResult(
                ruleName = "new_technology_signal",
                score = score.newTechnologyBonus,
                reason = "matched=${score.newTechnologyBonus > 0}",
                type = RecommendationRuleType.BONUS,
            ),
            RecommendationRuleResult(
                ruleName = "ai_llm_signal",
                score = score.aiLlmBonus,
                reason = "matched=${score.aiLlmBonus > 0}",
                type = RecommendationRuleType.BONUS,
            ),
            RecommendationRuleResult(
                ruleName = "platform_signal",
                score = score.platformBonus,
                reason = "matched=${score.platformBonus > 0}",
                type = RecommendationRuleType.BONUS,
            ),
            RecommendationRuleResult(
                ruleName = "signal_synergy",
                score = score.synergyBonus,
                reason =
                    "newTechnology=${score.newTechnologyBonus > 0}, " +
                        "aiLlm=${score.aiLlmBonus > 0}, platform=${score.platformBonus > 0}",
                type = RecommendationRuleType.BONUS,
            ),
            RecommendationRuleResult(
                ruleName = "reranking_recency",
                score = score.recencyBonus,
                reason = "publishedDate=${source.publishedDate}",
                type = RecommendationRuleType.BONUS,
            ),
        ).filter { it.score != 0.0 }
    }

    private fun calculateScore(source: RecommendCalculateSource): RerankingScore {
        val signalText =
            buildString {
                append(source.title).append(' ')
                append(source.provocativeHeadline).append(' ')
                append(source.summaryContent).append(' ')
                append(source.newsletterName).append(' ')
                append(source.contentProviderName.orEmpty()).append(' ')
                append(source.keywordNames.joinToString(" "))
            }

        val newTechnologyBonus = if (NEW_TECHNOLOGY_PATTERNS.any { it.containsMatchIn(signalText) }) NEW_TECHNOLOGY_BONUS else 0.0
        val aiLlmBonus = if (AI_LLM_PATTERNS.any { it.containsMatchIn(signalText) }) AI_LLM_BONUS else 0.0
        val platformBonus = if (PLATFORM_PATTERNS.any { it.containsMatchIn(signalText) }) PLATFORM_BONUS else 0.0
        val synergyBonus = calculateSynergyBonus(newTechnologyBonus, aiLlmBonus, platformBonus)
        val recencyBonus = calculateRecencyBonus(source.publishedDate)

        return RerankingScore(
            newTechnologyBonus = newTechnologyBonus,
            aiLlmBonus = aiLlmBonus,
            platformBonus = platformBonus,
            synergyBonus = synergyBonus,
            recencyBonus = recencyBonus,
        )
    }

    private fun calculateSynergyBonus(
        newTechnologyBonus: Double,
        aiLlmBonus: Double,
        platformBonus: Double,
    ): Double {
        val hasNewTechnologySignal = newTechnologyBonus > 0
        val hasAiLlmSignal = aiLlmBonus > 0
        val hasPlatformSignal = platformBonus > 0

        return when {
            hasNewTechnologySignal && hasAiLlmSignal && hasPlatformSignal -> STRONG_SYNERGY_BONUS
            hasNewTechnologySignal && (hasAiLlmSignal || hasPlatformSignal) -> MEDIUM_SYNERGY_BONUS
            hasAiLlmSignal && hasPlatformSignal -> LIGHT_SYNERGY_BONUS
            else -> 0.0
        }
    }

    private fun calculateRecencyBonus(publishedDate: LocalDate): Double {
        val daysOld = ChronoUnit.DAYS.between(publishedDate, todayProvider()).coerceAtLeast(0)
        return when {
            daysOld <= 1 -> 60.0
            daysOld <= 3 -> 45.0
            daysOld <= 7 -> 30.0
            daysOld <= 14 -> 15.0
            daysOld <= 30 -> 5.0
            else -> 0.0
        }
    }

    companion object {
        private const val NEW_TECHNOLOGY_BONUS = 40.0
        private const val AI_LLM_BONUS = 35.0
        private const val PLATFORM_BONUS = 25.0
        private const val STRONG_SYNERGY_BONUS = 30.0
        private const val MEDIUM_SYNERGY_BONUS = 20.0
        private const val LIGHT_SYNERGY_BONUS = 10.0

        private val NEW_TECHNOLOGY_PATTERNS =
            listOf(
                Regex(
                    """
                    (?ix)\b(
                        new|
                        launch(?:ed|es)?|
                        release(?:d|s)?|
                        introduc(?:e|ed|es|ing)|
                        announc(?:e|ed|es|ing)|
                        preview|beta|alpha|stable|ga|rc|
                        v\d+(?:\.\d+)*
                    )\b
                    """.trimIndent(),
                ),
                Regex("""출시|공개|발표|신규|새로운|최신|베타|정식|업데이트|릴리즈"""),
            )
        private val AI_LLM_PATTERNS =
            listOf(
                Regex(
                    """
                    (?ix)\b(
                        ai|llm|agent(?:ic|s)?|mcp|rag|vector|embedding|prompt|
                        copilot|claude|openai|gemini|anthropic|cursor|codex
                    )\b
                    """.trimIndent(),
                ),
                Regex("""인공지능|생성형\s*AI|언어\s*모델|에이전트|프롬프트|임베딩|벡터|코파일럿"""),
            )
        private val PLATFORM_PATTERNS =
            listOf(
                Regex(
                    """
                    (?ix)\b(
                        platform|api|sdk|framework|library|runtime|database|cloud|
                        kubernetes|postgresql|node(?:\.js)?|react|android|kotlin|
                        java|python|swift|wasm|browser|extension|infra(?:structure)?
                    )\b
                    """.trimIndent(),
                ),
                Regex("""플랫폼|API|SDK|프레임워크|라이브러리|런타임|데이터베이스|클라우드|인프라|확장\s*프로그램"""),
            )
    }

    private data class RerankingScore(
        val newTechnologyBonus: Double,
        val aiLlmBonus: Double,
        val platformBonus: Double,
        val synergyBonus: Double,
        val recencyBonus: Double,
    ) {
        val totalScore: Double
            get() = newTechnologyBonus + aiLlmBonus + platformBonus + synergyBonus + recencyBonus
    }
}

class FreshnessRule(
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : RecommendationRule {
    override val name: String = "freshness"

    override fun score(source: RecommendCalculateSource): Double = calculateScore(source)

    override fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult> {
        val daysOld = ChronoUnit.DAYS.between(source.publishedDate, todayProvider())
        val score = -daysOld * FRESHNESS_DECAY_PER_DAY

        return listOf(
            RecommendationRuleResult(
                ruleName = name,
                score = score,
                reason = "publishedDate=${source.publishedDate}, daysOld=$daysOld",
                type = if (score < 0) RecommendationRuleType.PENALTY else RecommendationRuleType.BONUS,
            ),
        )
    }

    companion object {
        private const val FRESHNESS_DECAY_PER_DAY = 10.0
    }

    private fun calculateScore(source: RecommendCalculateSource): Double {
        val daysOld = ChronoUnit.DAYS.between(source.publishedDate, todayProvider())
        return -daysOld * FRESHNESS_DECAY_PER_DAY
    }
}

class DuplicatePublisherPenaltyRule : RecommendationRule {
    override val name: String = "duplicate_publisher_penalty"

    override fun score(source: RecommendCalculateSource): Double = calculateScore(source)

    override fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult> {
        val score = calculateScore(source)

        return listOf(
            RecommendationRuleResult(
                ruleName = name,
                score = score,
                reason = "publisherDuplicateCandidateCount=${source.publisherDuplicateCandidateCount}",
                type = RecommendationRuleType.PENALTY,
            ),
        )
    }

    companion object {
        private const val DUPLICATE_PUBLISHER_PENALTY = 50.0
    }

    private fun calculateScore(source: RecommendCalculateSource): Double =
        -source.publisherDuplicateCandidateCount * DUPLICATE_PUBLISHER_PENALTY
}

class FinalScoreFloorRule : RecommendationRule {
    override val name: String = "final_score_floor"

    override fun score(source: RecommendCalculateSource): Double = 0.0

    override fun evaluate(source: RecommendCalculateSource): List<RecommendationRuleResult> =
        listOf(
            RecommendationRuleResult(
                ruleName = name,
                score = 0.0,
                reason = "applied by calculator when total score is below 0",
                type = RecommendationRuleType.ADJUSTMENT,
            ),
        )
}

private fun List<KeywordSource>.keywordNames(): String =
    mapNotNull { it.keywordName }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(",")
        ?: size.toString()
