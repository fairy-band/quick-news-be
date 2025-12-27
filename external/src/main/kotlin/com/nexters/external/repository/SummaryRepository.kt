package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.Summary
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SummaryRepository : JpaRepository<Summary, Long> {
    fun findByContent(content: Content): List<Summary>

    fun findByContent(
        content: Content,
        pageable: Pageable
    ): Page<Summary>

    /**
     * Content ID 목록으로 요약이 있는 Content ID 조회 (N+1 방지)
     */
    @Query(
        """
        SELECT DISTINCT s.content.id FROM Summary s
        WHERE s.content.id IN :contentIds
        """,
    )
    fun findContentIdsWithSummary(
        @Param("contentIds") contentIds: List<Long>,
    ): List<Long>
}
