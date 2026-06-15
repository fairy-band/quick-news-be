package com.nexters.external.service.keyword

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ContentKeywordMatchScore
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.enums.KeywordAliasMatchType
import com.nexters.external.enums.KeywordMatchSource
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentKeywordMatchScoreRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.service.category.ContentCategoryScoreService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentKeywordAutomationService(
    private val matchProviders: List<KeywordMatchProvider>,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val contentKeywordMatchScoreRepository: ContentKeywordMatchScoreRepository,
    private val contentCategoryScoreService: ContentCategoryScoreService,
) {
    private val logger = LoggerFactory.getLogger(ContentKeywordAutomationService::class.java)

    @Transactional
    fun assignKeywords(
        content: Content,
        aiMatchedKeywordNames: List<String> = emptyList(),
    ): ContentKeywordAssignmentResult {
        val contentId = content.id
        if (contentId == null) {
            logger.warn("Skipping keyword automation for unsaved content: {}", content.title)
            return ContentKeywordAssignmentResult(0, 0, 0, usedAiFallback = false)
        }

        val reservedKeywords = reservedKeywordRepository.findAll()
        if (reservedKeywords.isEmpty()) {
            logger.warn("No reserved keywords available for content keyword automation")
            contentCategoryScoreService.recalculateForContent(content)
            return ContentKeywordAssignmentResult(0, 0, 0, usedAiFallback = false)
        }

        val automaticCandidates =
            matchProviders
                .flatMap { provider -> provider.match(content, reservedKeywords) }
                .deduplicateByKeywordAndSource()

        val useAiFallback = aiMatchedKeywordNames.isNotEmpty() && shouldUseAiFallback(automaticCandidates)
        val aiFallbackCandidates =
            if (useAiFallback) {
                buildAiFallbackCandidates(aiMatchedKeywordNames, reservedKeywords, automaticCandidates)
            } else {
                emptyList()
            }

        val scoreCandidates = (automaticCandidates + aiFallbackCandidates).deduplicateByKeywordAndSource()
        val acceptedCandidates = scoreCandidates.toAcceptedCandidates()

        scoreCandidates
            .take(MAX_SCORE_RECORDS_PER_CONTENT)
            .forEach { candidate ->
                saveMatchScore(
                    contentId = contentId,
                    candidate = candidate,
                    accepted = acceptedCandidates.any { accepted -> accepted.keyword.id == candidate.keyword.id },
                )
            }

        acceptedCandidates.forEach { candidate ->
            assignKeywordToContent(content, candidate.keyword)
        }
        contentCategoryScoreService.recalculateForContent(content)

        return ContentKeywordAssignmentResult(
            automaticKeywordCount = automaticCandidates.size,
            aiFallbackKeywordCount = aiFallbackCandidates.size,
            acceptedKeywordCount = acceptedCandidates.size,
            usedAiFallback = useAiFallback,
        )
    }

    private fun shouldUseAiFallback(candidates: List<KeywordMatchCandidate>): Boolean =
        candidates.count { candidate -> candidate.confidence >= AUTO_ACCEPT_CONFIDENCE } < MIN_AUTOMATIC_KEYWORDS

    private fun buildAiFallbackCandidates(
        aiMatchedKeywordNames: List<String>,
        reservedKeywords: List<ReservedKeyword>,
        automaticCandidates: List<KeywordMatchCandidate>,
    ): List<KeywordMatchCandidate> {
        val automaticKeywordIds = automaticCandidates.mapNotNull { candidate -> candidate.keyword.id }.toSet()
        val reservedByName = reservedKeywords.associateBy { keyword -> keyword.name.normalizedKeywordName() }

        return aiMatchedKeywordNames
            .asSequence()
            .mapNotNull { name -> reservedByName[name.normalizedKeywordName()] }
            .filter { keyword -> keyword.id !in automaticKeywordIds }
            .distinctBy { keyword -> keyword.id }
            .map { keyword ->
                KeywordMatchCandidate(
                    keyword = keyword,
                    score = AI_FALLBACK_CONFIDENCE * 100.0,
                    confidence = AI_FALLBACK_CONFIDENCE,
                    source = KeywordMatchSource.AI_FALLBACK,
                    matchType = KeywordAliasMatchType.PHRASE,
                    matchedText = keyword.name,
                    reason = "AI_FALLBACK:accepted-generation-keyword",
                )
            }.toList()
    }

    private fun List<KeywordMatchCandidate>.deduplicateByKeywordAndSource(): List<KeywordMatchCandidate> =
        groupBy { candidate -> candidate.keyword.id to candidate.source }
            .values
            .map { candidates -> candidates.maxWith(compareBy<KeywordMatchCandidate> { it.confidence }.thenBy { it.score }) }
            .sortedWith(compareByDescending<KeywordMatchCandidate> { it.confidence }.thenByDescending { it.score })

    private fun List<KeywordMatchCandidate>.toAcceptedCandidates(): List<KeywordMatchCandidate> =
        groupBy { candidate -> candidate.keyword.id }
            .values
            .map { candidates -> candidates.maxWith(compareBy<KeywordMatchCandidate> { it.confidence }.thenBy { it.score }) }
            .filter { candidate -> candidate.confidence >= ACCEPTED_CONFIDENCE }
            .sortedWith(compareByDescending<KeywordMatchCandidate> { it.confidence }.thenByDescending { it.score })
            .take(MAX_ACCEPTED_KEYWORDS_PER_CONTENT)

    private fun saveMatchScore(
        contentId: Long,
        candidate: KeywordMatchCandidate,
        accepted: Boolean,
    ) {
        val keywordId = candidate.keyword.id ?: return
        val existing =
            contentKeywordMatchScoreRepository.findByContentIdAndKeywordIdAndSource(
                contentId = contentId,
                keywordId = keywordId,
                source = candidate.source,
            )

        if (existing == null) {
            contentKeywordMatchScoreRepository.save(
                ContentKeywordMatchScore(
                    contentId = contentId,
                    keywordId = keywordId,
                    score = candidate.score,
                    confidence = candidate.confidence,
                    source = candidate.source,
                    matchType = candidate.matchType,
                    matchedText = candidate.matchedText?.take(MAX_TEXT_COLUMN_LENGTH),
                    reason = candidate.reason.take(MAX_TEXT_COLUMN_LENGTH),
                    accepted = accepted,
                ),
            )
        } else {
            existing.score = candidate.score
            existing.confidence = candidate.confidence
            existing.matchedText = candidate.matchedText?.take(MAX_TEXT_COLUMN_LENGTH)
            existing.reason = candidate.reason.take(MAX_TEXT_COLUMN_LENGTH)
            existing.accepted = accepted
            existing.updatedAt = LocalDateTime.now()
            contentKeywordMatchScoreRepository.save(existing)
        }
    }

    private fun assignKeywordToContent(
        content: Content,
        keyword: ReservedKeyword,
    ) {
        if (contentKeywordMappingRepository.findByContentAndKeyword(content, keyword) != null) {
            return
        }

        contentKeywordMappingRepository.save(
            ContentKeywordMapping(
                content = content,
                keyword = keyword,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
    }

    private fun String.normalizedKeywordName(): String = trim().lowercase()

    companion object {
        private const val MIN_AUTOMATIC_KEYWORDS = 2
        private const val AUTO_ACCEPT_CONFIDENCE = 0.68
        private const val ACCEPTED_CONFIDENCE = 0.62
        private const val AI_FALLBACK_CONFIDENCE = 0.64
        private const val MAX_ACCEPTED_KEYWORDS_PER_CONTENT = 8
        private const val MAX_SCORE_RECORDS_PER_CONTENT = 20
        private const val MAX_TEXT_COLUMN_LENGTH = 1_000
    }
}
