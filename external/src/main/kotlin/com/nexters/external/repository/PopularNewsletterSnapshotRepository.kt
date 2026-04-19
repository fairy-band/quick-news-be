package com.nexters.external.repository

import com.nexters.external.entity.PopularNewsletterSnapshot
import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PopularNewsletterSnapshotRepository : JpaRepository<PopularNewsletterSnapshot, Long> {
    @Query(
        """
        SELECT s
        FROM PopularNewsletterSnapshot s
        WHERE s.segmentType = :segmentType
        AND s.status = :status
        AND ((:segmentKey IS NULL AND s.segmentKey IS NULL) OR s.segmentKey = :segmentKey)
        ORDER BY s.generatedAt DESC
        """,
    )
    fun findBySegmentTypeAndSegmentKeyAndStatusOrderByGeneratedAtDesc(
        @Param("segmentType") segmentType: PopularNewsletterSegmentType,
        @Param("segmentKey") segmentKey: String?,
        @Param("status") status: PopularNewsletterSnapshotStatus,
    ): List<PopularNewsletterSnapshot>
}
