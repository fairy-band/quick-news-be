package com.nexters.external.repository

import com.nexters.external.constants.ContentConstants.MAX_CONTENT_LENGTH
import com.nexters.external.entity.Content
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContentRepository : JpaRepository<Content, Long> {
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

    @Query(
        """
        SELECT c FROM Content c
        LEFT JOIN c.contentProvider cp
        WHERE c.id NOT IN (
            SELECT DISTINCT s.content.id FROM Summary s
        )
        AND LENGTH(c.content) <= $MAX_CONTENT_LENGTH
        ORDER BY
            CASE
                WHEN cp.type = 'BLOG' THEN 0
                WHEN cp.type = 'NEWSLETTER' THEN 1
                ELSE 2
            END,
            c.createdAt DESC
    """
    )
    fun findContentsWithoutSummaryOrderedByProviderTypePriority(pageable: Pageable): Page<Content>

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

    // RSS 피드용 메서드들
    fun findByOriginalUrl(originalUrl: String): Content?

    fun findByNewsletterName(newsletterName: String): List<Content>

    fun findByPublishedAtBetween(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): List<Content>

    fun existsByNewsletterSourceId(newsletterSourceId: String): Boolean

    fun findByNewsletterSourceId(newsletterSourceId: String): List<Content>
}
