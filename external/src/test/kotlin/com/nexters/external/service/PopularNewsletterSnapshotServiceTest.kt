package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.PopularNewsletterSnapshot
import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.PopularNewsletterSnapshotItemRepository
import com.nexters.external.repository.PopularNewsletterSnapshotRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `findLatestFeaturedExposureContent should return exposure content from latest featured snapshot`() {
        val latestFeaturedSnapshot =
            createSnapshot(
                id = 2L,
                generatedAt = LocalDateTime.of(2026, 4, 18, 7, 30),
                resolvedItemCount = 0,
                featuredExposureContentId = 11L,
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

        Mockito
            .`when`(
                popularNewsletterSnapshotRepository.findLatestFeaturedBySegmentTypeAndSegmentKeyAndStatus(
                    PopularNewsletterSegmentType.GLOBAL,
                    null,
                    PopularNewsletterSnapshotStatus.SUCCESS,
                    PageRequest.of(0, 1),
                ),
            ).thenReturn(listOf(latestFeaturedSnapshot))
        Mockito
            .`when`(exposureContentRepository.findById(11L))
            .thenReturn(Optional.of(featuredExposureContent))

        val result = sut.findLatestFeaturedExposureContent()

        assertSame(featuredExposureContent, result)
    }

    @Test
    fun `findLatestFeaturedExposureContent should return null when there is no resolved snapshot`() {
        Mockito
            .`when`(
                popularNewsletterSnapshotRepository.findLatestFeaturedBySegmentTypeAndSegmentKeyAndStatus(
                    PopularNewsletterSegmentType.GLOBAL,
                    null,
                    PopularNewsletterSnapshotStatus.SUCCESS,
                    PageRequest.of(0, 1),
                ),
            ).thenReturn(emptyList())

        val result = sut.findLatestFeaturedExposureContent()

        assertNull(result)
    }

    @Test
    fun `saveSnapshot should persist featured exposure content id from first resolved item`() {
        var savedSnapshot: PopularNewsletterSnapshot? = null

        Mockito
            .doAnswer { invocation ->
                invocation.getArgument<PopularNewsletterSnapshot>(0).also { snapshot ->
                    savedSnapshot = snapshot
                }
            }.`when`(popularNewsletterSnapshotRepository)
            .save(Mockito.any(PopularNewsletterSnapshot::class.java))
        Mockito
            .doAnswer { invocation -> invocation.getArgument(0) }
            .`when`(popularNewsletterSnapshotItemRepository)
            .saveAll(Mockito.anyList())

        val result =
            sut.saveSnapshot(
                SavePopularNewsletterSnapshotCommand(
                    segmentType = PopularNewsletterSegmentType.GLOBAL,
                    windowStartDate = LocalDate.of(2025, 4, 19),
                    windowEndDate = LocalDate.of(2026, 4, 18),
                    sourceEventName = "click_newsletter",
                    candidateLimit = 20,
                    resolvedItemCount = 1,
                    status = PopularNewsletterSnapshotStatus.SUCCESS,
                    items =
                        listOf(
                            SavePopularNewsletterSnapshotItemCommand(
                                rank = 2,
                                rawObjectId = "unresolved",
                                clickCount = 90L,
                                resolutionStatus = PopularNewsletterResolutionStatus.UNRESOLVED,
                            ),
                            SavePopularNewsletterSnapshotItemCommand(
                                rank = 1,
                                rawObjectId = "11",
                                clickCount = 120L,
                                resolvedContentId = 101L,
                                resolvedExposureContentId = 11L,
                                resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
                            ),
                        ),
                ),
            )

        assertEquals(11L, assertNotNull(savedSnapshot).featuredExposureContentId)
        assertEquals(11L, assertNotNull(result.featuredExposureContentId))
    }

    private fun createSnapshot(
        id: Long,
        generatedAt: LocalDateTime,
        resolvedItemCount: Int,
        featuredExposureContentId: Long? = null,
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
            featuredExposureContentId = featuredExposureContentId,
            status = PopularNewsletterSnapshotStatus.SUCCESS,
        )
}
