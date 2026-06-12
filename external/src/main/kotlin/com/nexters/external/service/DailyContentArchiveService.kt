package com.nexters.external.service

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.UserExposedContentMapping
import com.nexters.external.repository.DailyContentArchiveRepository
import com.nexters.external.repository.UserExposedContentMappingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DailyContentArchiveService(
    private val dailyContentArchiveRepository: DailyContentArchiveRepository,
    private val userExposedContentMappingRepository: UserExposedContentMappingRepository,
) {
    fun findByDateAndUserId(
        userId: Long,
        date: LocalDate,
    ) = dailyContentArchiveRepository.findByDateAndUserId(date, userId)

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
