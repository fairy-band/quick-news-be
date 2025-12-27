package com.nexters.admin.repository

import com.nexters.external.entity.Content
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Admin용 Content 조회 Repository
 */
@Repository
interface ContentAdminRepository : JpaRepository<Content, Long> {
    /**
     * 요약이 없는 콘텐츠 조회
     */
    @Query(
        """
        SELECT c FROM Content c
        WHERE c.id NOT IN (
            SELECT DISTINCT s.content.id FROM Summary s
        )
    """
    )
    fun findContentsWithoutSummary(pageable: Pageable): Page<Content>

    /**
     * 뉴스레터 이름으로 필터링하여 요약이 없는 콘텐츠 조회
     */
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

    /**
     * 뉴스레터 이름 목록 조회
     */
    @Query("SELECT DISTINCT c.newsletterName FROM Content c ORDER BY c.newsletterName")
    fun findDistinctNewsletterNames(): List<String>

    /**
     * 뉴스레터 이름으로 필터링
     */
    fun findByNewsletterName(
        newsletterName: String,
        pageable: Pageable
    ): Page<Content>

    /**
     * 카테고리별 콘텐츠 조회
     */
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

    /**
     * 카테고리별 요약 없는 콘텐츠 조회
     */
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

    /**
     * 카테고리와 뉴스레터 이름으로 요약 없는 콘텐츠 조회
     */
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

    /**
     * 카테고리와 뉴스레터 이름으로 콘텐츠 조회
     */
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

    /**
     * 키워드별 콘텐츠 조회
     */
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

    /**
     * Newsletter Source ID로 존재 여부 확인
     */
    fun existsByNewsletterSourceId(newsletterSourceId: String): Boolean

    /**
     * Newsletter Source ID로 콘텐츠 조회
     */
    fun findByNewsletterSourceId(newsletterSourceId: String): List<Content>

    /**
     * Original URL로 콘텐츠 조회
     */
    fun findByOriginalUrl(originalUrl: String): Content?
}
