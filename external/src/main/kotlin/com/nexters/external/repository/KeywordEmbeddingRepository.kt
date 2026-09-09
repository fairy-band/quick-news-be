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

    @Query(
        value = """
        WITH cat_centroids AS (
            SELECT category_id, AVG(embedding)::vector(1024) as centroid
            FROM keyword_embeddings
            WHERE category_id IS NOT NULL
            GROUP BY category_id
        )
        SELECT 
            cc.category_id AS categoryId,
            CAST(1.0 - (ce.embedding <=> cc.centroid) AS DOUBLE PRECISION) AS similarity
        FROM content_embeddings ce
        CROSS JOIN cat_centroids cc
        WHERE ce.content_id = :contentId
    """,
        nativeQuery = true,
    )
    fun findCategorySimilaritiesByContentId(
        @Param("contentId") contentId: Long,
    ): List<CategorySimilarityProjection>
}

interface KeywordEmbeddingMatchProjection {
    val keywordId: Long
    val keywordName: String
    val categoryName: String?
    val similarity: Double
}

interface CategorySimilarityProjection {
    val categoryId: Long
    val similarity: Double
}
