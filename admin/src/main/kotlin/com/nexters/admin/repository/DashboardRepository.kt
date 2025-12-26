package com.nexters.admin.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 대시보드용 Content 조회 Repository
 */
@Repository
interface DashboardContentRepository : JpaRepository<Content, Long> {
    /**
     * 기간별 콘텐츠 수 조회
     */
    fun countByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long

    /**
     * 기간별 콘텐츠 목록 조회
     */
    fun findAllByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Content>

    /**
     * 뉴스레터별 콘텐츠 수 조회 (상위 N개)
     */
    @Query(
        """
        SELECT c.newsletterName as name, COUNT(c) as count
        FROM Content c
        WHERE c.createdAt BETWEEN :startDate AND :endDate
        GROUP BY c.newsletterName
        ORDER BY COUNT(c) DESC
        """
    )
    fun countByNewsletterNameBetween(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<NewsletterDistribution>

    /**
     * 전체 뉴스레터별 콘텐츠 수 조회
     */
    @Query(
        """
        SELECT c.newsletterName as name, COUNT(c) as count
        FROM Content c
        GROUP BY c.newsletterName
        ORDER BY COUNT(c) DESC
        """
    )
    fun countByNewsletterName(): List<NewsletterDistribution>

    /**
     * 일별 콘텐츠 수 조회
     */
    @Query(
        """
        SELECT DATE(c.createdAt) as date, COUNT(c) as count
        FROM Content c
        WHERE c.createdAt BETWEEN :startDate AND :endDate
        GROUP BY DATE(c.createdAt)
        ORDER BY DATE(c.createdAt)
        """
    )
    fun countByDateBetween(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<DailyContentCount>

    /**
     * 최근 생성된 콘텐츠 조회
     */
    @Query(
        """
        SELECT c FROM Content c
        ORDER BY c.createdAt DESC
        """
    )
    fun findRecentContents(): List<Content>

    /**
     * 기간별 최근 생성된 콘텐츠 조회
     */
    @Query(
        """
        SELECT c FROM Content c
        WHERE c.createdAt BETWEEN :startDate AND :endDate
        ORDER BY c.createdAt DESC
        """
    )
    fun findRecentContentsBetween(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<Content>

    /**
     * 뉴스레터별 기간 필터링 콘텐츠 조회
     */
    fun findByNewsletterNameAndCreatedAtBetween(
        newsletterName: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Content>

    /**
     * 뉴스레터별 기간 필터링 콘텐츠 수 조회
     */
    fun countByNewsletterNameAndCreatedAtBetween(
        newsletterName: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long

    /**
     * 카테고리별 콘텐츠 수 조회 (기간 필터링)
     */
    @Query(
        """
        SELECT cat.name as name, COUNT(DISTINCT c) as count
        FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        JOIN Category cat ON catkm.category.id = cat.id
        WHERE c.createdAt BETWEEN :startDate AND :endDate
        GROUP BY cat.name
        ORDER BY COUNT(DISTINCT c) DESC
        """
    )
    fun countByCategoryBetween(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<CategoryDistribution>

    /**
     * 전체 카테고리별 콘텐츠 수 조회
     */
    @Query(
        """
        SELECT cat.name as name, COUNT(DISTINCT c) as count
        FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        JOIN Category cat ON catkm.category.id = cat.id
        GROUP BY cat.name
        ORDER BY COUNT(DISTINCT c) DESC
        """
    )
    fun countByCategory(): List<CategoryDistribution>
}

/**
 * 대시보드용 ExposureContent 조회 Repository
 */
@Repository
interface DashboardExposureContentRepository : JpaRepository<ExposureContent, Long> {
    /**
     * 기간별 노출 콘텐츠 수 조회
     */
    fun countByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long

    /**
     * 기간별 노출 콘텐츠 목록 조회
     */
    fun findAllByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<ExposureContent>

    /**
     * 기간별 최근 노출 콘텐츠 조회
     */
    @Query(
        """
        SELECT e FROM ExposureContent e
        WHERE e.createdAt BETWEEN :startDate AND :endDate
        ORDER BY e.createdAt DESC
        """
    )
    fun findRecentExposureContentsBetween(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<ExposureContent>
}

/**
 * 대시보드용 Summary 조회 Repository
 */
@Repository
interface DashboardSummaryRepository : JpaRepository<Summary, Long> {
    /**
     * 기간별 요약 수 조회
     */
    fun countByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long

    /**
     * 기간별 요약 목록 조회
     */
    fun findAllByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Summary>

    /**
     * 요약 생성 시간 기준 기간별 수 조회
     */
    fun countBySummarizedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long

    /**
     * Content로 Summary 조회
     */
    fun findByContent(content: Content): List<Summary>

    /**
     * 여러 Content의 Summary를 한 번에 조회 (N+1 방지)
     */
    @Query(
        """
        SELECT s FROM Summary s
        WHERE s.content IN :contents
        """
    )
    fun findByContentIn(@Param("contents") contents: List<Content>): List<Summary>

    /**
     * Content ID 목록으로 요약이 있는 Content ID 조회 (N+1 방지)
     */
    @Query(
        """
        SELECT DISTINCT s.content.id FROM Summary s
        WHERE s.content.id IN :contentIds
        """
    )
    fun findContentIdsWithSummary(@Param("contentIds") contentIds: List<Long>): List<Long>
}

/**
 * 대시보드용 ReservedKeyword 조회 Repository
 */
@Repository
interface DashboardKeywordRepository : JpaRepository<ReservedKeyword, Long> {
    /**
     * 기간별 키워드 수 조회
     */
    fun countByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Long

    /**
     * 기간별 키워드 목록 조회
     */
    fun findAllByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<ReservedKeyword>
}

/**
 * 뉴스레터 분포 데이터 인터페이스
 */
interface NewsletterDistribution {
    val name: String
    val count: Long
}

/**
 * 일별 콘텐츠 수 데이터 인터페이스
 */
interface DailyContentCount {
    val date: java.time.LocalDate
    val count: Long
}

/**
 * 카테고리 분포 데이터 인터페이스
 */
interface CategoryDistribution {
    val name: String
    val count: Long
}
