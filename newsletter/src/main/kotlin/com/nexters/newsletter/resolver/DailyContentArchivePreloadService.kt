package com.nexters.newsletter.resolver

import com.nexters.external.service.DailyContentArchiveService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DailyContentArchivePreloadService(
    private val dailyContentArchiveService: DailyContentArchiveService,
    private val dailyContentArchiveResolver: DailyContentArchiveResolver,
) {
    fun preloadFromPreviousArchive(
        previousDate: LocalDate,
        targetDate: LocalDate,
        maxUsers: Int,
    ): DailyContentArchivePreloadResult {
        if (maxUsers <= 0) {
            return DailyContentArchivePreloadResult(
                previousDate = previousDate,
                targetDate = targetDate,
                candidateUserCount = 0,
                processedUserCount = 0,
                createdCount = 0,
                skippedExistingCount = 0,
                failedUserIds = emptyList(),
            )
        }

        val candidateUserIds =
            dailyContentArchiveService
                .findUserIdsByDate(previousDate)
                .take(maxUsers)

        var createdCount = 0
        var skippedExistingCount = 0
        val failedUserIds = mutableListOf<Long>()

        candidateUserIds.forEach { userId ->
            if (dailyContentArchiveService.existsByDateAndUserId(userId, targetDate)) {
                skippedExistingCount++
                return@forEach
            }

            try {
                dailyContentArchiveResolver.resolveTodayContentArchive(userId, targetDate)
                createdCount++
            } catch (e: Exception) {
                failedUserIds += userId
                logger.warn(
                    "daily_archive_preload_failed userId={} previousDate={} targetDate={}",
                    userId,
                    previousDate,
                    targetDate,
                    e,
                )
            }
        }

        return DailyContentArchivePreloadResult(
            previousDate = previousDate,
            targetDate = targetDate,
            candidateUserCount = candidateUserIds.size,
            processedUserCount = candidateUserIds.size,
            createdCount = createdCount,
            skippedExistingCount = skippedExistingCount,
            failedUserIds = failedUserIds,
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DailyContentArchivePreloadService::class.java)
    }
}

data class DailyContentArchivePreloadResult(
    val previousDate: LocalDate,
    val targetDate: LocalDate,
    val candidateUserCount: Int,
    val processedUserCount: Int,
    val createdCount: Int,
    val skippedExistingCount: Int,
    val failedUserIds: List<Long>,
) {
    val failedCount: Int
        get() = failedUserIds.size
}
