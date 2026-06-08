package com.nexters.newsletter.resolver

import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RerankingBonusCalculator {
    fun calculate(source: RerankingBonusSource): Double {
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

        return newTechnologyBonus + aiLlmBonus + platformBonus + synergyBonus + recencyBonus
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
        val daysOld = ChronoUnit.DAYS.between(publishedDate, LocalDate.now()).coerceAtLeast(0)
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
}

data class RerankingBonusSource(
    val title: String,
    val provocativeHeadline: String,
    val summaryContent: String,
    val newsletterName: String,
    val contentProviderName: String?,
    val keywordNames: List<String>,
    val publishedDate: LocalDate,
)
