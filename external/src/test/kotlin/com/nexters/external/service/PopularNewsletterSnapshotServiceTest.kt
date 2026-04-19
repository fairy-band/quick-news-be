package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.PopularNewsletterSnapshot
import com.nexters.external.entity.PopularNewsletterSnapshotItem
import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.PopularNewsletterSnapshotItemRepository
import com.nexters.external.repository.PopularNewsletterSnapshotRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertNull
import kotlin.test.assertSame

class PopularNewsletterSnapshotServiceTest {
    private val popularNewsletterSnapshotRepository: PopularNewsletterSnapshotRepository =
        Mockito.mock(PopularNewsletterSnapshotRepository::class.java)
    private val popularNewsletterSnapshotItemRepository: PopularNewsletterSnapshotItemRepository =
        Mockito.mock(PopularNewsletterSnapshotItemRepository::class.java)
    private val exposureContentRepository: ExposureContentRepository =
        Mockito.mock(ExposureContentRepository::class.java)

    private val sut =
        PopularNewsletterSnapshotService(
            popularNewsletterSnapshotRepository = popularNewsletterSnapshotRepository,
            popularNewsletterSnapshotItemRepository = popularNewsletterSnapshotItemRepository,
            exposureContentRepository = exposureContentRepository,
        )

    @Test
    fun `findLatestFeaturedExposureContent should fall back to older resolved snapshot`() {
        val latestSnapshot =
            createSnapshot(
                id = 2L,
                generatedAt = LocalDateTime.of(2026, 4, 18, 7, 30),
                resolvedItemCount = 0,
            )
        val olderSnapshot =
            createSnapshot(
                id = 1L,
                generatedAt = LocalDateTime.of(2026, 4, 17, 7, 30),
                resolvedItemCount = 1,
            )
        val featuredExposureContent =
            ExposureContent(
                id = 11L,
                content =
                    Content(
                        id = 101L,
                        title = "title",
                        content = "content",
                        newsletterName = "newsletter",
                        originalUrl = "https://example.com/articles/101",
                        imageUrl = null,
                        publishedAt = LocalDate.of(2026, 4, 18),
                        contentProvider = null,
                    ),
                provocativeKeyword = "keyword",
                provocativeHeadline = "headline",
                summaryContent = "summary",
            )
        val olderResolvedItem =
            PopularNewsletterSnapshotItem(
                id = 21L,
                snapshot = olderSnapshot,
                rank = 1,
                rawObjectId = "11",
                clickCount = 120L,
                resolvedContentId = 101L,
                resolvedExposureContentId = 11L,
                resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
            )

        Mockito
            .`when`(
                popularNewsletterSnapshotRepository.findBySegmentTypeAndSegmentKeyAndStatusOrderByGeneratedAtDesc(
                    PopularNewsletterSegmentType.GLOBAL,
                    null,
                    PopularNewsletterSnapshotStatus.SUCCESS,
                ),
            ).thenReturn(listOf(latestSnapshot, olderSnapshot))
        Mockito
            .`when`(
                popularNewsletterSnapshotItemRepository.findFirstBySnapshotIdAndResolutionStatusOrderByRankAsc(
                    1L,
                    PopularNewsletterResolutionStatus.RESOLVED,
                ),
            ).thenReturn(olderResolvedItem)
        Mockito
            .`when`(exposureContentRepository.findById(11L))
            .thenReturn(Optional.of(featuredExposureContent))

        val result = sut.findLatestFeaturedExposureContent()

        assertSame(featuredExposureContent, result)
        Mockito
            .verify(popularNewsletterSnapshotItemRepository, Mockito.never())
            .findFirstBySnapshotIdAndResolutionStatusOrderByRankAsc(
                2L,
                PopularNewsletterResolutionStatus.RESOLVED,
            )
    }

    @Test
    fun `findLatestFeaturedExposureContent should return null when there is no resolved snapshot`() {
        Mockito
            .`when`(
                popularNewsletterSnapshotRepository.findBySegmentTypeAndSegmentKeyAndStatusOrderByGeneratedAtDesc(
                    PopularNewsletterSegmentType.GLOBAL,
                    null,
                    PopularNewsletterSnapshotStatus.SUCCESS,
                ),
            ).thenReturn(emptyList())

        val result = sut.findLatestFeaturedExposureContent()

        assertNull(result)
    }

    private fun createSnapshot(
        id: Long,
        generatedAt: LocalDateTime,
        resolvedItemCount: Int,
    ): PopularNewsletterSnapshot =
        PopularNewsletterSnapshot(
            id = id,
            segmentType = PopularNewsletterSegmentType.GLOBAL,
            windowStartDate = LocalDate.of(2025, 4, 19),
            windowEndDate = LocalDate.of(2026, 4, 18),
            generatedAt = generatedAt,
            sourceEventName = "click_newsletter",
            candidateLimit = 20,
            resolvedItemCount = resolvedItemCount,
            status = PopularNewsletterSnapshotStatus.SUCCESS,
        )
}
