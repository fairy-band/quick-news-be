package com.nexters.external.repository

import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface KeywordEmbeddingRepository : JpaRepository<ReservedKeyword, Long> {
    @Query(
        value = """
        SELECT 
            ke.keyword_id AS keywordId,
            ke.name AS keywordName,
            ke.category_name AS categoryName,
            (1 - (ke.embedding <=> ce.embedding)) AS similarity
        FROM keyword_embeddings ke
        JOIN content_embeddings ce ON ce.content_id = :contentId
        WHERE (1 - (ke.embedding <=> ce.embedding)) >= :minSimilarity
        ORDER BY (ke.embedding <=> ce.embedding) ASC
        LIMIT :limit
    """,
        nativeQuery = true,
    )
    fun findSimilarKeywordsByContentId(
        @Param("contentId") contentId: Long,
        @Param("minSimilarity") minSimilarity: Double,
        @Param("limit") limit: Int,
    ): List<KeywordEmbeddingMatchProjection>
}

interface KeywordEmbeddingMatchProjection {
    val keywordId: Long
    val keywordName: String
    val categoryName: String?
    val similarity: Double
}
