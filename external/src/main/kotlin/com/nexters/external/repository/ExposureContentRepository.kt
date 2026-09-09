package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface ExposureContentRepository : JpaRepository<ExposureContent, Long> {
    fun findByContent(content: Content): ExposureContent?

    fun findByContentId(contentId: Long): ExposureContent?

    @Query(
        """
        SELECT new com.nexters.external.repository.ExposureContentLookupRow(
            e.id,
            e.content.id
        )
        FROM ExposureContent e
        WHERE e.id IN :exposureContentIds
    """,
    )
    fun findLookupRowsByIds(
        @Param("exposureContentIds") exposureContentIds: Collection<Long>,
    ): List<ExposureContentLookupRow>

    @Query(
        """
        SELECT e
        FROM ExposureContent e
        WHERE e.provocativeHeadline IN :headlines
    """
    )
    fun findByProvocativeHeadlineIn(
        @Param("headlines") headlines: Collection<String>,
    ): List<ExposureContent>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExposureContentLookupRow(
            e.id,
            e.content.id
        )
        FROM ExposureContent e
        WHERE e.content.id IN :contentIds
    """,
    )
    fun findLookupRowsByContentIds(
        @Param("contentIds") contentIds: Collection<Long>,
    ): List<ExposureContentLookupRow>

    // 모든 ExposureContent를 페이징으로 조회
    @Query(
        """
        SELECT e FROM ExposureContent e
        ORDER BY e.id DESC
    """
    )
    fun findAllPaged(pageable: Pageable): Page<ExposureContent>

    // provocativeKeyword로 필터링하여 페이징 조회
    @Query(
        """
        SELECT e FROM ExposureContent e
        WHERE e.provocativeKeyword = :keyword
        ORDER BY e.id DESC
    """
    )
    fun findByProvocativeKeyword(
        @Param("keyword") keyword: String,
        pageable: Pageable
    ): Page<ExposureContent>

    // provocativeKeyword가 "No Keywords"인 항목 개수 조회
    @Query(
        """
        SELECT COUNT(e) FROM ExposureContent e
        WHERE e.provocativeKeyword = 'No Keywords'
    """
    )
    fun countByNoKeywords(): Long

    @Query(
        """
        SELECT e FROM ExposureContent e
        WHERE e.id < :lastSeenOffset OR :lastSeenOffset = 0
        ORDER BY e.id DESC
    """
    )
    fun findAllWithOffset(
        @Param("lastSeenOffset") lastSeenOffset: Long,
        pageable: Pageable
    ): Page<ExposureContent>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
    """,
    )
    fun findExploreRows(pageable: Pageable): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE e.id < :lastSeenOffset
    """,
    )
    fun findExploreRowsAfter(
        @Param("lastSeenOffset") lastSeenOffset: Long,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE e.id > :lastSeenOffset
    """,
    )
    fun findExploreRowsAfterAscending(
        @Param("lastSeenOffset") lastSeenOffset: Long,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE c.publishedAt < :lastSeenPublishedAt
    """,
    )
    fun findExploreRowsAfterByPublishedAt(
        @Param("lastSeenPublishedAt") lastSeenPublishedAt: LocalDate,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE c.publishedAt > :lastSeenPublishedAt
    """,
    )
    fun findExploreRowsAfterByPublishedAtAscending(
        @Param("lastSeenPublishedAt") lastSeenPublishedAt: LocalDate,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id AND ccs.categoryId IN :categoryIds AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
    """,
    )
    fun findExploreRowsByCategoryIds(
        @Param("categoryIds") categoryIds: List<Long>,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE e.id < :lastSeenOffset
        AND EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id AND ccs.categoryId IN :categoryIds AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
    """,
    )
    fun findExploreRowsAfterByCategoryIds(
        @Param("lastSeenOffset") lastSeenOffset: Long,
        @Param("categoryIds") categoryIds: List<Long>,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE e.id > :lastSeenOffset
        AND EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id AND ccs.categoryId IN :categoryIds AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
    """,
    )
    fun findExploreRowsAfterAscendingByCategoryIds(
        @Param("lastSeenOffset") lastSeenOffset: Long,
        @Param("categoryIds") categoryIds: List<Long>,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE c.publishedAt < :lastSeenPublishedAt
        AND EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id AND ccs.categoryId IN :categoryIds AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
    """,
    )
    fun findExploreRowsAfterByPublishedAtByCategoryIds(
        @Param("lastSeenPublishedAt") lastSeenPublishedAt: LocalDate,
        @Param("categoryIds") categoryIds: List<Long>,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExploreContentRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.language,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE c.publishedAt > :lastSeenPublishedAt
        AND EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id AND ccs.categoryId IN :categoryIds AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
    """,
    )
    fun findExploreRowsAfterByPublishedAtAscendingByCategoryIds(
        @Param("lastSeenPublishedAt") lastSeenPublishedAt: LocalDate,
        @Param("categoryIds") categoryIds: List<Long>,
        pageable: Pageable,
    ): List<ExploreContentRow>

    @Query(
        """
        SELECT COUNT(e)
        FROM ExposureContent e
        JOIN e.content c
        WHERE EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id AND ccs.categoryId IN :categoryIds AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
    """,
    )
    fun countByCategoryIds(
        @Param("categoryIds") categoryIds: List<Long>,
    ): Long

    @Query(
        """
        SELECT c FROM Content c
        WHERE c.id IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsWithExposure(pageable: Pageable): Page<Content>

    @Query(
        """
        SELECT c FROM Content c
        WHERE c.id NOT IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsWithoutExposure(pageable: Pageable): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
        AND c.id IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsByCategoryWithExposure(
        @Param("categoryId") categoryId: Long,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
        AND c.id NOT IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsByCategoryWithoutExposure(
        @Param("categoryId") categoryId: Long,
        pageable: Pageable
    ): Page<Content>

    // 뉴스레터 이름으로 필터링하는 메서드 추가
    @Query(
        """
        SELECT c FROM Content c
        WHERE c.newsletterName = :newsletterName
        AND c.id IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsWithExposureByNewsletterName(
        @Param("newsletterName") newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT c FROM Content c
        WHERE c.newsletterName = :newsletterName
        AND c.id NOT IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsWithoutExposureByNewsletterName(
        @Param("newsletterName") newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
        AND c.newsletterName = :newsletterName
        AND c.id IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsByCategoryWithExposureAndNewsletterName(
        @Param("categoryId") categoryId: Long,
        @Param("newsletterName") newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
        AND c.newsletterName = :newsletterName
        AND c.id NOT IN (
            SELECT DISTINCT e.content.id FROM ExposureContent e
        )
    """
    )
    fun findContentsByCategoryWithoutExposureAndNewsletterName(
        @Param("categoryId") categoryId: Long,
        @Param("newsletterName") newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExposureContentRecommendationCandidateRow(
            e.id,
            c.id,
            cp.id,
            cp.name,
            c.newsletterName,
            c.publishedAt,
            c.title,
            e.provocativeHeadline,
            e.summaryContent
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE c.publishedAt >= :publishedFrom
        AND TRIM(c.content) NOT LIKE '(cat /tmp/trans_%'
        AND EXISTS (
            SELECT 1 FROM ContentKeywordMapping ckm
            WHERE ckm.content = c
            AND ckm.keyword.id IN :reservedKeywordIds
        )
        AND NOT EXISTS (
            SELECT 1 FROM UserExposedContentMapping uecm
            WHERE uecm.contentId = c.id
            AND uecm.userId = :userId
        )
        ORDER BY c.publishedAt DESC, e.id DESC
    """
    )
    fun findNotExposedRecommendationCandidatesByReservedKeywordIds(
        @Param("userId") userId: Long,
        @Param("reservedKeywordIds") reservedKeywordIds: List<Long>,
        @Param("publishedFrom") publishedFrom: LocalDate,
        pageable: Pageable,
    ): List<ExposureContentRecommendationCandidateRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExposureContentRecommendationCandidateRow(
            e.id,
            c.id,
            cp.id,
            cp.name,
            c.newsletterName,
            c.publishedAt,
            c.title,
            e.provocativeHeadline,
            e.summaryContent
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE cp.id IN :contentProviderIds
        AND c.publishedAt >= :publishedFrom
        AND TRIM(c.content) NOT LIKE '(cat /tmp/trans_%'
        AND NOT EXISTS (
            SELECT 1 FROM UserExposedContentMapping uecm
            WHERE uecm.contentId = c.id
            AND uecm.userId = :userId
        )
        ORDER BY c.publishedAt DESC, e.id DESC
    """
    )
    fun findNotExposedRecommendationCandidatesByContentProviderIds(
        @Param("userId") userId: Long,
        @Param("contentProviderIds") contentProviderIds: List<Long>,
        @Param("publishedFrom") publishedFrom: LocalDate,
        pageable: Pageable,
    ): List<ExposureContentRecommendationCandidateRow>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExposureContentRecommendationCandidateRow(
            e.id,
            c.id,
            cp.id,
            cp.name,
            c.newsletterName,
            c.publishedAt,
            c.title,
            e.provocativeHeadline,
            e.summaryContent
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE c.publishedAt >= :publishedFrom
        AND TRIM(c.content) NOT LIKE '(cat /tmp/trans_%'
        AND EXISTS (
            SELECT 1 FROM ContentCategoryScore ccs
            WHERE ccs.contentId = c.id
            AND ccs.categoryId IN :categoryIds
            AND ccs.totalScore > 0
            AND ccs.providerMismatch = false
            AND (ccs.competingScore < ccs.totalScore * 1.5 OR ccs.competingScore - ccs.totalScore < 8.0)
        )
        AND NOT EXISTS (
            SELECT 1 FROM UserExposedContentMapping uecm
            WHERE uecm.contentId = c.id
            AND uecm.userId = :userId
        )
        ORDER BY c.publishedAt DESC, e.id DESC
    """
    )
    fun findNotExposedRecommendationCandidatesByCategoryScoreCategoryIds(
        @Param("userId") userId: Long,
        @Param("categoryIds") categoryIds: List<Long>,
        @Param("publishedFrom") publishedFrom: LocalDate,
        pageable: Pageable,
    ): List<ExposureContentRecommendationCandidateRow>

    @Query(
        """
        SELECT e FROM ExposureContent e
        JOIN FETCH e.content c
        LEFT JOIN FETCH c.contentProvider
        WHERE e.id IN :exposureContentIds
    """
    )
    fun findByIdsWithContent(
        @Param("exposureContentIds") exposureContentIds: List<Long>,
    ): List<ExposureContent>

    @Query(
        """
        SELECT new com.nexters.external.repository.ExposureContentArchiveRow(
            e.id,
            c.id,
            e.provocativeKeyword,
            e.provocativeHeadline,
            e.summaryContent,
            c.originalUrl,
            c.imageUrl,
            c.newsletterName,
            cp.id,
            cp.language,
            cp.type,
            e.createdAt,
            e.updatedAt
        )
        FROM ExposureContent e
        JOIN e.content c
        LEFT JOIN c.contentProvider cp
        WHERE e.id IN :exposureContentIds
    """
    )
    fun findArchiveRowsByIds(
        @Param("exposureContentIds") exposureContentIds: List<Long>,
    ): List<ExposureContentArchiveRow>

    /**
     * Content ID 목록으로 노출된 Content ID 조회 (N+1 방지)
     */
    @Query(
        """
        SELECT DISTINCT e.content.id FROM ExposureContent e
        WHERE e.content.id IN :contentIds
        """,
    )
    fun findContentIdsWithExposure(
        @Param("contentIds") contentIds: List<Long>,
    ): List<Long>

    @Query(
        """
        SELECT e FROM ExposureContent e
        JOIN FETCH e.content c
        WHERE NOT EXISTS (
            SELECT 1 FROM ExposureContentMarkdown m
            WHERE m.exposureContentId = e.id
        )
        ORDER BY e.id DESC
    """
    )
    fun findExposureContentsWithoutMarkdown(pageable: Pageable): List<ExposureContent>

    @Query(
        value = """
        SELECT 
            e.id AS exposureContentId,
            c.id AS contentId,
            cp.id AS contentProviderId,
            cp.name AS contentProviderName,
            c.newsletter_name AS newsletterName,
            c.published_at AS publishedAt,
            c.title AS title,
            e.provocative_headline AS provocativeHeadline,
            e.summary_content AS summaryContent
        FROM exposure_contents e
        JOIN contents c ON c.id = e.content_id
        LEFT JOIN content_provider cp ON cp.id = c.content_provider_id
        JOIN content_embeddings ce ON ce.content_id = c.id
        WHERE c.published_at >= :publishedFrom
        AND btrim(c.content) NOT LIKE '(cat /tmp/trans_%'
        AND NOT EXISTS (
            SELECT 1 FROM user_exposed_contents_mapping uecm 
            WHERE uecm.content_id = c.id AND uecm.user_id = :userId
        )
        AND EXISTS (
            SELECT 1 FROM content_category_scores ccs
            WHERE ccs.content_id = c.id
            AND ccs.category_id IN (:categoryIds)
            AND ccs.total_score > 0
            AND ccs.provider_mismatch = false
        )
        ORDER BY ce.embedding <=> (
            COALESCE(
                (
                    SELECT AVG(vec)::vector(1024)
                    FROM (
                        -- 1. Up to 5 most recent read articles (Dynamic Current Interest)
                        (SELECT ce2.embedding as vec
                         FROM user_exposed_contents_mapping u2 
                         JOIN content_embeddings ce2 ON ce2.content_id = u2.content_id 
                         WHERE u2.user_id = :userId 
                         ORDER BY u2.created_at DESC 
                         LIMIT 5)
                        UNION ALL
                        -- 2. Onboarding keywords & experience vectors (Core Long-Term Persona Anchor)
                        (SELECT ke.embedding as vec
                         FROM user_keyword_mappings ukm 
                         JOIN keyword_embeddings ke ON ke.keyword_id = ukm.keyword_id 
                         WHERE ukm.user_id = :userId)
                    ) combined
                ),
                -- Fallback: Category Core 1st Anchor
                (SELECT ce3.embedding FROM content_category_scores ccs3
                 JOIN content_embeddings ce3 ON ce3.content_id = ccs3.content_id
                 WHERE ccs3.category_id IN (:categoryIds) ORDER BY ccs3.total_score DESC LIMIT 1)
            )
        ) ASC
        LIMIT :limit
    """,
        nativeQuery = true,
    )
    fun findNotExposedSemanticRecommendationCandidates(
        @Param("userId") userId: Long,
        @Param("categoryIds") categoryIds: List<Long>,
        @Param("publishedFrom") publishedFrom: LocalDate,
        @Param("limit") limit: Int,
    ): List<ExposureContentRecommendationCandidateProjection>
    @Query(
        value = """
        SELECT 
            ce1.content_id AS contentId1,
            ce2.content_id AS contentId2,
            CAST(1.0 - (ce1.embedding <=> ce2.embedding) AS DOUBLE PRECISION) AS similarity
        FROM content_embeddings ce1
        JOIN content_embeddings ce2 ON ce1.content_id < ce2.content_id
        WHERE ce1.content_id IN (:contentIds)
          AND ce2.content_id IN (:contentIds)
          AND (1.0 - (ce1.embedding <=> ce2.embedding)) >= :minSimilarity
    """,
        nativeQuery = true,
    )
    fun findSimilarContentPairs(
        @Param("contentIds") contentIds: Collection<Long>,
        @Param("minSimilarity") minSimilarity: Double = 0.80,
    ): List<ContentSimilarityPairProjection>
}

interface ExposureContentRecommendationCandidateProjection {
    val exposureContentId: Long
    val contentId: Long
    val contentProviderId: Long?
    val contentProviderName: String?
    val newsletterName: String
    val publishedAt: LocalDate
    val title: String
    val provocativeHeadline: String
    val summaryContent: String
}

interface ContentSimilarityPairProjection {
    val contentId1: Long
    val contentId2: Long
    val similarity: Double
}
