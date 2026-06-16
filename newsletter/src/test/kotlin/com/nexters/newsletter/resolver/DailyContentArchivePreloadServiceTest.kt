package com.nexters.newsletter.resolver

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.service.DailyContentArchiveService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyContentArchivePreloadServiceTest {
    private val dailyContentArchiveService = mockk<DailyContentArchiveService>()
    private val dailyContentArchiveResolver = mockk<DailyContentArchiveResolver>()
    private val service =
        DailyContentArchivePreloadService(
            dailyContentArchiveService = dailyContentArchiveService,
            dailyContentArchiveResolver = dailyContentArchiveResolver,
        )

    @Test
    fun `preloadFromPreviousArchive should create missing target archives and continue after failures`() {
        val previousDate = LocalDate.of(2026, 6, 16)
        val targetDate = LocalDate.of(2026, 6, 17)

        every { dailyContentArchiveService.findUserIdsByDate(previousDate) } returns listOf(1L, 2L, 3L, 4L)
        every { dailyContentArchiveService.existsByDateAndUserId(1L, targetDate) } returns true
        every { dailyContentArchiveService.existsByDateAndUserId(2L, targetDate) } returns false
        every { dailyContentArchiveService.existsByDateAndUserId(3L, targetDate) } returns false
        every { dailyContentArchiveResolver.resolveTodayContentArchive(2L, targetDate) } returns mockk<DailyContentArchive>()
        every { dailyContentArchiveResolver.resolveTodayContentArchive(3L, targetDate) } throws IllegalStateException("boom")

        val result =
            service.preloadFromPreviousArchive(
                previousDate = previousDate,
                targetDate = targetDate,
                maxUsers = 3,
            )

        assertThat(result.candidateUserCount).isEqualTo(3)
        assertThat(result.processedUserCount).isEqualTo(3)
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.skippedExistingCount).isEqualTo(1)
        assertThat(result.failedUserIds).containsExactly(3L)
        assertThat(result.failedCount).isEqualTo(1)
        verify(exactly = 0) { dailyContentArchiveService.existsByDateAndUserId(4L, targetDate) }
        verify(exactly = 1) { dailyContentArchiveResolver.resolveTodayContentArchive(2L, targetDate) }
        verify(exactly = 1) { dailyContentArchiveResolver.resolveTodayContentArchive(3L, targetDate) }
    }

    @Test
    fun `preloadFromPreviousArchive should skip work when maxUsers is not positive`() {
        val previousDate = LocalDate.of(2026, 6, 16)
        val targetDate = LocalDate.of(2026, 6, 17)

        val result =
            service.preloadFromPreviousArchive(
                previousDate = previousDate,
                targetDate = targetDate,
                maxUsers = 0,
            )

        assertThat(result.candidateUserCount).isZero()
        assertThat(result.processedUserCount).isZero()
        assertThat(result.createdCount).isZero()
        assertThat(result.skippedExistingCount).isZero()
        assertThat(result.failedUserIds).isEmpty()
        verify(exactly = 0) { dailyContentArchiveService.findUserIdsByDate(any()) }
    }
}
