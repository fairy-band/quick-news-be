package com.nexters.api.batch.config

import com.nexters.newsletter.resolver.DailyContentArchivePreloadService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class DailyContentArchivePreloadScheduler(
    private val dailyContentArchivePreloadService: DailyContentArchivePreloadService,
) {
    private val logger = LoggerFactory.getLogger(DailyContentArchivePreloadScheduler::class.java)

    @Scheduled(
        cron = DAILY_ARCHIVE_PRELOAD_CRON,
        zone = DAILY_ARCHIVE_PRELOAD_ZONE,
    )
    fun preloadTodayArchives() {
        val targetDate = LocalDate.now(ZoneId.of(DAILY_ARCHIVE_PRELOAD_ZONE))
        val previousDate = targetDate.minusDays(1)

        logger.info(
            "daily_archive_preload_started previousDate={} targetDate={} maxUsers={}",
            previousDate,
            targetDate,
            MAX_PRELOAD_USERS,
        )

        val result =
            dailyContentArchivePreloadService.preloadFromPreviousArchive(
                previousDate = previousDate,
                targetDate = targetDate,
                maxUsers = MAX_PRELOAD_USERS,
            )

        logger.info(
            "daily_archive_preload_completed previousDate={} targetDate={} candidateUserCount={} " +
                "processedUserCount={} createdCount={} skippedExistingCount={} failedCount={} failedUserIds={}",
            result.previousDate,
            result.targetDate,
            result.candidateUserCount,
            result.processedUserCount,
            result.createdCount,
            result.skippedExistingCount,
            result.failedCount,
            result.failedUserIds,
        )
    }

    companion object {
        private const val DAILY_ARCHIVE_PRELOAD_CRON = "0 5 0 * * *"
        private const val DAILY_ARCHIVE_PRELOAD_ZONE = "Asia/Seoul"
        private const val MAX_PRELOAD_USERS = 200
    }
}
