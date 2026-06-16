package com.nexters.external.service

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.UserExposedContentMapping
import com.nexters.external.repository.DailyContentArchiveRepository
import com.nexters.external.repository.UserExposedContentMappingRepository
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

private val dailyContentArchiveServiceLogger = LoggerFactory.getLogger(DailyContentArchiveService::class.java)
private const val SLOW_ARCHIVE_SAVE_LOG_THRESHOLD_MS = 500L

@Service
class DailyContentArchiveService(
    private val dailyContentArchiveRepository: DailyContentArchiveRepository,
    private val userExposedContentMappingRepository: UserExposedContentMappingRepository,
    private val mongoTemplate: MongoTemplate,
) {
    fun findByDateAndUserId(
        userId: Long,
        date: LocalDate,
    ) = dailyContentArchiveRepository.findByDateAndUserId(date, userId)

    fun existsByDateAndUserId(
        userId: Long,
        date: LocalDate,
    ): Boolean = dailyContentArchiveRepository.existsByUserIdAndDate(userId, date)

    fun findUserIdsByDate(date: LocalDate): List<Long> =
        mongoTemplate
            .query(DailyContentArchive::class.java)
            .distinct("user._id")
            .matching(Query.query(Criteria.where("date").`is`(date)))
            .`as`(Number::class.java)
            .all()
            .map { it.toLong() }

    @Transactional
    fun saveWithHistory(dailyContentArchive: DailyContentArchive): DailyContentArchive {
        val trace = DailyContentArchiveSaveTrace()
        val mappings =
            dailyContentArchive.exposureContents.map {
                UserExposedContentMapping(
                    userId = dailyContentArchive.user.id,
                    contentId = it.content.id,
                )
            }

        trace.measure("saveExposureHistory") {
            if (mappings.isNotEmpty()) {
                userExposedContentMappingRepository.saveAll(mappings)
            }
        }

        return trace
            .measure("saveArchiveDocument") {
                dailyContentArchiveRepository.save(dailyContentArchive)
            }.also {
                trace.logIfSlow(dailyContentArchive)
            }
    }

    @Transactional
    fun deleteByDateAndUserId(
        userId: Long,
        date: LocalDate,
    ) {
        userExposedContentMappingRepository.markActiveAsDeletedByUserIdAndCreatedAtRange(
            userId = userId,
            startAt = date.atStartOfDay(),
            endAt = date.plusDays(1).atStartOfDay(),
        )

        dailyContentArchiveRepository.deleteByDateAndUserId(date, userId)
    }

    fun isRefreshAvailable(
        userId: Long,
        date: LocalDate
    ): Boolean =
        !userExposedContentMappingRepository.existsDeletedByUserIdAndCreatedAtRange(
            userId = userId,
            startAt = date.atStartOfDay(),
            endAt = date.plusDays(1).atStartOfDay(),
        )
}

private class DailyContentArchiveSaveTrace {
    private val timings = linkedMapOf<String, Long>()

    fun <T> measure(
        operation: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            timings[operation] = (System.nanoTime() - startedAt) / 1_000_000
        }
    }

    fun logIfSlow(dailyContentArchive: DailyContentArchive) {
        val totalMillis = timings.values.sum()
        if (totalMillis < SLOW_ARCHIVE_SAVE_LOG_THRESHOLD_MS) {
            return
        }

        dailyContentArchiveServiceLogger.info(
            "daily_archive_save_completed userId={} date={} exposureContentCount={} timingsMs={}",
            dailyContentArchive.user.id,
            dailyContentArchive.date,
            dailyContentArchive.exposureContents.size,
            timings,
        )
    }
}
