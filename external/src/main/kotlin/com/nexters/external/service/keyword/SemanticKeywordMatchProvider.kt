package com.nexters.external.service.keyword

import com.nexters.external.entity.Content
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.enums.KeywordAliasMatchType
import com.nexters.external.enums.KeywordMatchSource
import com.nexters.external.repository.KeywordEmbeddingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SemanticKeywordMatchProvider(
    private val keywordEmbeddingRepository: KeywordEmbeddingRepository,
) : KeywordMatchProvider {
    private val logger = LoggerFactory.getLogger(SemanticKeywordMatchProvider::class.java)

    override fun match(
        content: Content,
        reservedKeywords: List<ReservedKeyword>,
    ): List<KeywordMatchCandidate> {
        val contentId = content.id ?: return emptyList()
        val keywordsById = reservedKeywords.mapNotNull { keyword -> keyword.id?.let { it to keyword } }.toMap()

        return try {
            val matches =
                keywordEmbeddingRepository.findSimilarKeywordsByContentId(
                    contentId = contentId,
                    minSimilarity = MIN_SEMANTIC_SIMILARITY,
                    limit = MAX_SEMANTIC_MATCH_COUNT,
                )

            matches.mapNotNull { row ->
                val keyword = keywordsById[row.keywordId] ?: return@mapNotNull null
                val sim = row.similarity
                KeywordMatchCandidate(
                    keyword = keyword,
                    score = sim * 100.0,
                    confidence = sim,
                    source = KeywordMatchSource.EMBEDDING,
                    matchType = KeywordAliasMatchType.EXACT,
                    matchedText = row.keywordName,
                    reason = "SEMANTIC_EMBEDDING:cosine_similarity=${String.format("%.3f", sim)}",
                )
            }
        } catch (e: Exception) {
            logger.warn("시맨틱 키워드 임베딩 매칭 실패 (contentId: {}): {}", contentId, e.message)
            emptyList()
        }
    }

    companion object {
        private const val MIN_SEMANTIC_SIMILARITY = 0.45
        private const val MAX_SEMANTIC_MATCH_COUNT = 4
    }
}
