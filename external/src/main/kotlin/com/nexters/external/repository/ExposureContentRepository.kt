package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ExposureContentRepository : JpaRepository<ExposureContent, Long> {
    fun findByContent(content: Content): ExposureContent?

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
        SELECT DISTINCT e FROM ExposureContent e
        JOIN FETCH e.content c
        LEFT JOIN FETCH c.contentProvider
        LEFT JOIN FETCH c.reservedKeywords
        JOIN ContentKeywordMapping ckm ON c = ckm.content
        LEFT JOIN UserExposedContentMapping uecm ON c.id = uecm.contentId AND uecm.userId = :userId
        WHERE uecm.contentId IS NULL
        AND ckm.keyword.id IN :reservedKeywordIds
    """
    )
    fun findNotExposedByReservedKeywordIds(
        @Param("userId") userId: Long,
        @Param("reservedKeywordIds") reservedKeywordIds: List<Long>
    ): List<ExposureContent>

    @Query(
        """
        SELECT DISTINCT e FROM ExposureContent e
        JOIN FETCH e.content c
        LEFT JOIN FETCH c.contentProvider
        LEFT JOIN FETCH c.reservedKeywords
        LEFT JOIN UserExposedContentMapping uecm ON c.id = uecm.contentId AND uecm.userId = :userId
        WHERE uecm.contentId IS NULL
        AND c.contentProvider.id IN :contentProviderIds
    """
    )
    fun findNotExposedByContentProviderIds(
        @Param("userId") userId: Long,
        @Param("contentProviderIds") contentProviderIds: List<Long>
    ): List<ExposureContent>

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
}
