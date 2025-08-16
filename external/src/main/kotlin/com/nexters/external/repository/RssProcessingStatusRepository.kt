package com.nexters.external.repository

import com.nexters.external.entity.RssProcessingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface RssProcessingStatusRepository : JpaRepository<RssProcessingStatus, Long> {
    fun findByItemUrl(itemUrl: String): RssProcessingStatus?

    fun findByNewsletterSourceId(newsletterSourceId: String): RssProcessingStatus?

    fun findByAiProcessedFalseAndIsProcessedTrue(pageable: Pageable): Page<RssProcessingStatus>

    @Query(
        "SELECT r FROM RssProcessingStatus r WHERE r.aiProcessed = false AND r.isProcessed = true ORDER BY r.priority DESC, r.createdAt DESC"
    )
    fun findUnprocessedByPriority(pageable: Pageable): Page<RssProcessingStatus>

    @Query("SELECT COUNT(r) FROM RssProcessingStatus r WHERE r.aiProcessedAt >= :startDate")
    fun countProcessedToday(startDate: LocalDateTime): Long

    fun findByRssUrl(rssUrl: String): List<RssProcessingStatus>
}
