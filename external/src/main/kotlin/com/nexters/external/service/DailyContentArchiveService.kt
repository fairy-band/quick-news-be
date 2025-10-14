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
        dailyContentArchive.exposureContents.forEach {
            userExposedContentMappingRepository.save(
                UserExposedContentMapping(
                    userId = dailyContentArchive.user.id!!,
                    contentId = it.content.id!!,
                ),
            )
        }

        return dailyContentArchiveRepository.save(dailyContentArchive)
    }

    @Transactional
    fun deleteByDateAndUserId(
        userId: Long,
        date: LocalDate,
    ) {
        val mappings = userExposedContentMappingRepository.findByUserIdAndDate(userId, date.atStartOfDay())
        mappings.forEach { it.deleted = true }
        userExposedContentMappingRepository.saveAll(mappings)

        dailyContentArchiveRepository.deleteByDateAndUserId(date, userId)
    }

    fun isRefreshAvailable(
        userId: Long,
        date: LocalDate
    ): Boolean = userExposedContentMappingRepository.findDeletedByUserIdAndDate(userId, date).isEmpty()
}
