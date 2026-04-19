package com.nexters.external.service

import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.PopularNewsletterSnapshot
import com.nexters.external.entity.PopularNewsletterSnapshotItem
import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.PopularNewsletterSnapshotItemRepository
import com.nexters.external.repository.PopularNewsletterSnapshotRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class PopularNewsletterSnapshotService(
    private val popularNewsletterSnapshotRepository: PopularNewsletterSnapshotRepository,
    private val popularNewsletterSnapshotItemRepository: PopularNewsletterSnapshotItemRepository,
    private val exposureContentRepository: ExposureContentRepository,
) {
    @Transactional
    fun saveSnapshot(command: SavePopularNewsletterSnapshotCommand): PopularNewsletterSnapshot {
        val featuredExposureContentId =
            command.items
                .asSequence()
                .sortedBy { it.rank }
                .firstOrNull { item ->
                    item.resolutionStatus == PopularNewsletterResolutionStatus.RESOLVED &&
                        item.resolvedExposureContentId != null
                }?.resolvedExposureContentId

        val snapshot =
            popularNewsletterSnapshotRepository.save(
                PopularNewsletterSnapshot(
                    segmentType = command.segmentType,
                    segmentKey = command.segmentKey,
                    windowStartDate = command.windowStartDate,
                    windowEndDate = command.windowEndDate,
                    generatedAt = command.generatedAt,
                    sourceEventName = command.sourceEventName,
                    candidateLimit = command.candidateLimit,
                    resolvedItemCount = command.resolvedItemCount,
                    featuredExposureContentId = featuredExposureContentId,
                    status = command.status,
                ),
            )

        if (command.items.isNotEmpty()) {
            popularNewsletterSnapshotItemRepository.saveAll(
                command.items.map { item ->
                    PopularNewsletterSnapshotItem(
                        snapshot = snapshot,
                        rank = item.rank,
                        rawObjectId = item.rawObjectId,
                        clickCount = item.clickCount,
                        resolvedContentId = item.resolvedContentId,
                        resolvedExposureContentId = item.resolvedExposureContentId,
                        resolutionStatus = item.resolutionStatus,
                    )
                },
            )
        }

        return snapshot
    }

    @Transactional(readOnly = true)
    fun findLatestFeaturedExposureContent(
        segmentType: PopularNewsletterSegmentType = PopularNewsletterSegmentType.GLOBAL,
        segmentKey: String? = null,
    ): ExposureContent? {
        val latestSnapshot =
            popularNewsletterSnapshotRepository
                .findLatestFeaturedBySegmentTypeAndSegmentKeyAndStatus(
                    segmentType = segmentType,
                    segmentKey = segmentKey,
                    status = PopularNewsletterSnapshotStatus.SUCCESS,
                    pageable = PageRequest.of(0, 1),
                ).firstOrNull()
                ?: return null

        return latestSnapshot.featuredExposureContentId
            ?.let { exposureContentId -> exposureContentRepository.findById(exposureContentId).orElse(null) }
    }
}

data class SavePopularNewsletterSnapshotCommand(
    val segmentType: PopularNewsletterSegmentType,
    val segmentKey: String? = null,
    val windowStartDate: LocalDate,
    val windowEndDate: LocalDate,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val sourceEventName: String,
    val candidateLimit: Int,
    val resolvedItemCount: Int,
    val status: PopularNewsletterSnapshotStatus,
    val items: List<SavePopularNewsletterSnapshotItemCommand> = emptyList(),
)

data class SavePopularNewsletterSnapshotItemCommand(
    val rank: Int,
    val rawObjectId: String,
    val clickCount: Long,
    val resolvedContentId: Long? = null,
    val resolvedExposureContentId: Long? = null,
    val resolutionStatus: PopularNewsletterResolutionStatus,
)
