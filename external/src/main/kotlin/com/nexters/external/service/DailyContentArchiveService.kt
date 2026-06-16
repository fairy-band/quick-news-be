package com.nexters.external.service

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.UserExposedContentMapping
import com.nexters.external.repository.DailyContentArchiveRepository
import com.nexters.external.repository.UserExposedContentMappingRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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
        val mappings =
            dailyContentArchive.exposureContents.map {
                UserExposedContentMapping(
                    userId = dailyContentArchive.user.id,
                    contentId = it.content.id,
                )
            }

        userExposedContentMappingRepository.saveAll(mappings)

        return dailyContentArchiveRepository.save(dailyContentArchive)
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
