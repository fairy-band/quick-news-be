package com.nexters.external.repository

import com.nexters.external.entity.Content
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContentRepository : JpaRepository<Content, Long> {
    @Query(
        """
        SELECT c
        FROM Content c
        JOIN ContentKeywordMapping ckm ON c = ckm.content
        WHERE ckm.keyword.id IN :reservedKeywordIds
    """
    )
    fun findByReservedKeywordIds(reservedKeywordIds: List<Long>): List<Content>

    @Query(
        """
        SELECT c
        FROM Content c
        JOIN ContentKeywordMapping ckm ON c = ckm.content
        WHERE ckm.keyword.id IN :reservedKeywordIds
    """
    )
    fun findByReservedKeywordIds(
        reservedKeywordIds: List<Long>,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
    """
    )
    fun findContentsByCategory(
        @Param("categoryId") categoryId: Long
    ): List<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
    """
    )
    fun findContentsByCategory(
        @Param("categoryId") categoryId: Long,
        pageable: Pageable
    ): Page<Content>

    @Query("SELECT DISTINCT c.newsletterName FROM Content c ORDER BY c.newsletterName")
    fun findDistinctNewsletterNames(): List<String>

    @Query(
        """
        SELECT c FROM Content c
        WHERE c.id NOT IN (
            SELECT DISTINCT s.content.id FROM Summary s
        )
    """
    )
    fun findContentsWithoutSummary(pageable: Pageable): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
        AND c.id NOT IN (
            SELECT DISTINCT s.content.id FROM Summary s
        )
    """
    )
    fun findContentsByCategoryWithoutSummary(
        @Param("categoryId") categoryId: Long,
        pageable: Pageable
    ): Page<Content>

    // 뉴스레터 이름으로 필터링하는 메서드 추가
    fun findByNewsletterName(
        newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT c FROM Content c
        WHERE c.newsletterName = :newsletterName
        AND c.id NOT IN (
            SELECT DISTINCT s.content.id FROM Summary s
        )
    """
    )
    fun findContentsWithoutSummaryByNewsletterName(
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
            SELECT DISTINCT s.content.id FROM Summary s
        )
    """
    )
    fun findContentsByCategoryWithoutSummaryAndNewsletterName(
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
    """
    )
    fun findContentsByCategoryAndNewsletterName(
        @Param("categoryId") categoryId: Long,
        @Param("newsletterName") newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        WHERE ckm.keyword.id = :keywordId
        """
    )
    fun findContentsByKeywordId(
        @Param("keywordId") keywordId: Long,
        pageable: Pageable
    ): Page<Content>

    @Query(
        """
        SELECT c FROM Content c
         JOIN ContentKeywordMapping ckm ON c = ckm.content
        WHERE c.id NOT IN (
            SELECT uecm.contentId FROM UserExposedContentMapping uecm
            WHERE uecm.userId = :userId
        )
         AND ckm.keyword.id IN :reservedKeywordIds
        """
    )
    fun findNotExposedContents(
        @Param("userId") userId: Long,
        reservedKeywordIds: List<Long>
    ): List<Content>

    // RSS 피드용 메서드들
    fun findByOriginalUrl(originalUrl: String): Content?

    fun findByNewsletterName(newsletterName: String): List<Content>

    fun findByPublishedAtBetween(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): List<Content>
}
