package com.nexters.external.repository

import com.nexters.external.entity.PopularNewsletterSnapshotItem
import com.nexters.external.enums.PopularNewsletterResolutionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PopularNewsletterSnapshotItemRepository : JpaRepository<PopularNewsletterSnapshotItem, Long> {
    fun findFirstBySnapshotIdAndResolutionStatusOrderByRankAsc(
        snapshotId: Long,
        resolutionStatus: PopularNewsletterResolutionStatus,
    ): PopularNewsletterSnapshotItem?
}
