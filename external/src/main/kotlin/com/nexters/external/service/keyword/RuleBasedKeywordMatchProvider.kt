package com.nexters.external.service.keyword

import com.nexters.external.entity.Content
import com.nexters.external.entity.KeywordAlias
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.enums.KeywordAliasMatchType
import com.nexters.external.enums.KeywordMatchSource
import com.nexters.external.repository.KeywordAliasRepository
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class RuleBasedKeywordMatchProvider(
    private val keywordAliasRepository: KeywordAliasRepository,
) : KeywordMatchProvider {
    override fun match(
        content: Content,
        reservedKeywords: List<ReservedKeyword>,
    ): List<KeywordMatchCandidate> {
        val keywordsById = reservedKeywords.mapNotNull { keyword -> keyword.id?.let { it to keyword } }.toMap()
        val aliasRules = keywordAliasRepository.findByEnabledTrue().mapNotNull { alias -> alias.toRule(keywordsById) }
        val reservedNameRules = reservedKeywords.mapNotNull { keyword -> keyword.toReservedNameRule() }
        val fields = content.toMatchFields()

        return (aliasRules + reservedNameRules)
            .asSequence()
            .filter { rule -> rule.matchType != KeywordAliasMatchType.EMBEDDING_HINT }
            .flatMap { rule -> fields.asSequence().mapNotNull { field -> rule.match(field) } }
            .groupBy { candidate -> candidate.keyword.id }
            .values
            .map { candidates -> candidates.maxWith(compareBy<KeywordMatchCandidate> { it.confidence }.thenBy { it.score }) }
    }

    private fun KeywordAlias.toRule(keywordsById: Map<Long, ReservedKeyword>): KeywordRule? {
        val keyword = keywordsById[keywordId] ?: return null
        return KeywordRule(
            keyword = keyword,
            alias = alias.trim(),
            normalizedAlias = normalizedAlias.ifBlank { alias.trim().lowercase() },
            matchType = matchType,
            weight = weight,
            targetFields = targetFields.toMatchFields(matchType),
            caseSensitive = caseSensitive,
            source =
                if (matchType == KeywordAliasMatchType.SOURCE) {
                    KeywordMatchSource.SOURCE_HINT
                } else {
                    KeywordMatchSource.ALIAS
                },
        )
    }

    private fun ReservedKeyword.toReservedNameRule(): KeywordRule? {
        val keywordId = id ?: return null
        val normalizedName = name.trim().lowercase()
        if (normalizedName.isBlank()) return null

        return KeywordRule(
            keyword = this,
            alias = name.trim(),
            normalizedAlias = normalizedName,
            matchType = KeywordAliasMatchType.PHRASE,
            weight = 1.0,
            targetFields = MatchField.entries.toSet(),
            caseSensitive = normalizedName.isAmbiguousShortToken(),
            source = KeywordMatchSource.RESERVED_NAME,
            ruleId = keywordId,
        )
    }

    private fun KeywordRule.match(field: MatchFieldText): KeywordMatchCandidate? {
        if (field.field !in targetFields) return null
        val matchedText = findMatchedText(field) ?: return null
        val confidence = calculateConfidence(field)
        return KeywordMatchCandidate(
            keyword = keyword,
            score = confidence * 100.0,
            confidence = confidence,
            source = source,
            matchType = matchType,
            matchedText = matchedText.take(MAX_MATCHED_TEXT_LENGTH),
            reason = "${source.name}:${matchType.name}:${field.field.name}",
        )
    }

    private fun KeywordRule.findMatchedText(field: MatchFieldText): String? =
        when (matchType) {
            KeywordAliasMatchType.REGEX -> regexMatch(field.text)
            KeywordAliasMatchType.EXACT,
            KeywordAliasMatchType.PHRASE,
            KeywordAliasMatchType.SOURCE,
            -> phraseMatch(field)
            KeywordAliasMatchType.EMBEDDING_HINT -> null
        }

    private fun KeywordRule.regexMatch(text: String): String? =
        runCatching {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(alias, options).find(text)?.value
        }.getOrNull()

    private fun KeywordRule.phraseMatch(field: MatchFieldText): String? {
        if (alias.isBlank()) return null
        if (alias.requiresAsciiBoundary()) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            return Regex("""(?<![A-Za-z0-9])${Regex.escape(alias)}(?![A-Za-z0-9])""", options)
                .find(field.text)
                ?.value
        }

        val haystack = if (caseSensitive) field.text else field.normalizedText
        val needle = if (caseSensitive) alias else normalizedAlias
        val index = haystack.indexOf(needle)
        if (index < 0) return null
        return field.text.substring(index, min(index + alias.length, field.text.length))
    }

    private fun KeywordRule.calculateConfidence(field: MatchFieldText): Double {
        val sourceBase =
            when (source) {
                KeywordMatchSource.ALIAS -> 0.66
                KeywordMatchSource.SOURCE_HINT -> 0.62
                KeywordMatchSource.RESERVED_NAME -> 0.56
                KeywordMatchSource.EMBEDDING -> 0.64
                KeywordMatchSource.AI_FALLBACK -> 0.58
            }
        val typeBonus =
            when (matchType) {
                KeywordAliasMatchType.EXACT -> 0.08
                KeywordAliasMatchType.PHRASE -> 0.05
                KeywordAliasMatchType.REGEX -> 0.07
                KeywordAliasMatchType.SOURCE -> 0.06
                KeywordAliasMatchType.EMBEDDING_HINT -> 0.0
            }
        val weightBonus = min(weight, MAX_RULE_WEIGHT) * 0.03
        return min(MAX_CONFIDENCE, sourceBase + field.confidenceBonus + typeBonus + weightBonus)
    }

    private fun Content.toMatchFields(): List<MatchFieldText> =
        listOf(
            MatchFieldText(MatchField.TITLE, title, 0.22),
            MatchFieldText(MatchField.CONTENT, content.take(MAX_CONTENT_CHARS), 0.0),
            MatchFieldText(MatchField.URL, originalUrl, 0.10),
            MatchFieldText(
                MatchField.SOURCE,
                listOfNotNull(
                    newsletterName,
                    contentProvider?.name,
                    contentProvider?.channel,
                ).joinToString(" "),
                0.16,
            ),
        )

    private fun String.toMatchFields(matchType: KeywordAliasMatchType): Set<MatchField> {
        if (matchType == KeywordAliasMatchType.SOURCE) return setOf(MatchField.SOURCE)

        val fields =
            split(",")
                .mapNotNull { value -> runCatching { MatchField.valueOf(value.trim().uppercase()) }.getOrNull() }
                .toSet()

        return fields.ifEmpty { MatchField.entries.toSet() }
    }

    private fun String.requiresAsciiBoundary(): Boolean = any { it.isAsciiLetterOrDigit() }

    private fun String.isAmbiguousShortToken(): Boolean = length <= 2 && all { it.isLetter() }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

    private data class KeywordRule(
        val keyword: ReservedKeyword,
        val alias: String,
        val normalizedAlias: String,
        val matchType: KeywordAliasMatchType,
        val weight: Double,
        val targetFields: Set<MatchField>,
        val caseSensitive: Boolean,
        val source: KeywordMatchSource,
        @Suppress("unused") val ruleId: Long? = null,
    )

    private data class MatchFieldText(
        val field: MatchField,
        val text: String,
        val confidenceBonus: Double,
    ) {
        val normalizedText: String = text.lowercase()
    }

    private enum class MatchField {
        TITLE,
        CONTENT,
        URL,
        SOURCE,
    }

    companion object {
        private const val MAX_CONTENT_CHARS = 6_000
        private const val MAX_MATCHED_TEXT_LENGTH = 255
        private const val MAX_RULE_WEIGHT = 4.0
        private const val MAX_CONFIDENCE = 0.98
    }
}
