package com.nexters.api.batch.service

import com.nexters.api.batch.dto.NewsletterClick
import com.nexters.external.entity.PopularNewsletterSnapshot
import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.enums.PopularNewsletterSegmentType
import com.nexters.external.enums.PopularNewsletterSnapshotStatus
import com.nexters.external.service.PopularNewsletterObjectIdResolverService
import com.nexters.external.service.PopularNewsletterObjectResolution
import com.nexters.external.service.PopularNewsletterSnapshotService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PopularNewsletterRankingBatchServiceTest {
    private val googleAnalyticsService: GoogleAnalyticsService = Mockito.mock(GoogleAnalyticsService::class.java)
    private val popularNewsletterObjectIdResolverService: PopularNewsletterObjectIdResolverService =
        Mockito.mock(PopularNewsletterObjectIdResolverService::class.java)
    private val popularNewsletterSnapshotService: PopularNewsletterSnapshotService =
        Mockito.mock(PopularNewsletterSnapshotService::class.java)

    private val sut =
        PopularNewsletterRankingBatchService(
            googleAnalyticsService = googleAnalyticsService,
            popularNewsletterObjectIdResolverService = popularNewsletterObjectIdResolverService,
            popularNewsletterSnapshotService = popularNewsletterSnapshotService,
        )

    @Test
    fun `rebuildGlobalRanking should save success snapshot with resolved and unresolved items`() {
        val endDate = LocalDate.of(2026, 4, 18)
        var savedCommand: com.nexters.external.service.SavePopularNewsletterSnapshotCommand? = null
        val savedSnapshot =
            PopularNewsletterSnapshot(
                id = 100L,
                segmentType = PopularNewsletterSegmentType.GLOBAL,
                windowStartDate = LocalDate.of(2025, 4, 19),
                windowEndDate = endDate,
                generatedAt = LocalDateTime.of(2026, 4, 18, 7, 30),
                sourceEventName = PopularNewsletterRankingBatchService.NEWSLETTER_CLICK_EVENT_NAME,
                candidateLimit = 20,
                resolvedItemCount = 1,
                status = PopularNewsletterSnapshotStatus.SUCCESS,
            )

        Mockito
            .`when`(googleAnalyticsService.getTopNewsletterClicksForRollingWindow(endDate, 365, 20))
            .thenReturn(
                listOf(
                    NewsletterClick(objectId = "11", clickCount = 120L),
                    NewsletterClick(objectId = "unresolved-object", clickCount = 95L),
                ),
            )
        Mockito
            .`when`(popularNewsletterObjectIdResolverService.resolveObjectId("11"))
            .thenReturn(
                PopularNewsletterObjectResolution(
                    resolvedContentId = 101L,
                    resolvedExposureContentId = 11L,
                    resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
                ),
            )
        Mockito
            .`when`(popularNewsletterObjectIdResolverService.resolveObjectId("unresolved-object"))
            .thenReturn(
                PopularNewsletterObjectResolution(
                    resolutionStatus = PopularNewsletterResolutionStatus.UNRESOLVED,
                ),
            )
        Mockito
            .doAnswer { invocation ->
                savedCommand = invocation.getArgument(0)
                savedSnapshot
            }.`when`(popularNewsletterSnapshotService)
            .saveSnapshot(any())

        val result = sut.rebuildGlobalRanking(endDate, 365, 20)

        assertSame(savedSnapshot, result)

        val command = assertNotNull(savedCommand)
        assertEquals(PopularNewsletterSegmentType.GLOBAL, command.segmentType)
        assertEquals(LocalDate.of(2025, 4, 19), command.windowStartDate)
        assertEquals(endDate, command.windowEndDate)
        assertEquals(PopularNewsletterRankingBatchService.NEWSLETTER_CLICK_EVENT_NAME, command.sourceEventName)
        assertEquals(20, command.candidateLimit)
        assertEquals(1, command.resolvedItemCount)
        assertEquals(PopularNewsletterSnapshotStatus.SUCCESS, command.status)
        assertEquals(2, command.items.size)
        assertEquals(1, command.items[0].rank)
        assertEquals("11", command.items[0].rawObjectId)
        assertEquals(120L, command.items[0].clickCount)
        assertEquals(11L, command.items[0].resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.RESOLVED, command.items[0].resolutionStatus)
        assertEquals(2, command.items[1].rank)
        assertEquals("unresolved-object", command.items[1].rawObjectId)
        assertEquals(PopularNewsletterResolutionStatus.UNRESOLVED, command.items[1].resolutionStatus)
    }

    @Test
    fun `rebuildGlobalRanking should save failed snapshot and throw when ga query fails`() {
        val endDate = LocalDate.of(2026, 4, 18)
        var savedCommand: com.nexters.external.service.SavePopularNewsletterSnapshotCommand? = null

        Mockito
            .`when`(googleAnalyticsService.getTopNewsletterClicksForRollingWindow(endDate, 365, 20))
            .thenThrow(RuntimeException("GA error"))
        Mockito
            .doAnswer { invocation ->
                savedCommand = invocation.getArgument(0)
                PopularNewsletterSnapshot(
                    id = 101L,
                    segmentType = PopularNewsletterSegmentType.GLOBAL,
                    windowStartDate = LocalDate.of(2025, 4, 19),
                    windowEndDate = endDate,
                    generatedAt = LocalDateTime.of(2026, 4, 18, 7, 30),
                    sourceEventName = PopularNewsletterRankingBatchService.NEWSLETTER_CLICK_EVENT_NAME,
                    candidateLimit = 20,
                    resolvedItemCount = 0,
                    status = PopularNewsletterSnapshotStatus.FAILED,
                )
            }.`when`(popularNewsletterSnapshotService)
            .saveSnapshot(any())

        val exception =
            assertThrows<IllegalStateException> {
                sut.rebuildGlobalRanking(endDate, 365, 20)
            }

        assertTrue(exception.message!!.contains("인기 뉴스레터 랭킹 스냅샷 생성"))

        val command = assertNotNull(savedCommand)
        assertEquals(PopularNewsletterSnapshotStatus.FAILED, command.status)
        assertEquals(0, command.resolvedItemCount)
        assertTrue(command.items.isEmpty())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> any(): T {
        Mockito.any<T>()
        return null as T
    }
}
