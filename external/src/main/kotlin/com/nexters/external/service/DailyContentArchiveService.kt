package com.nexters.external.service

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.repository.DailyContentArchiveRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DailyContentArchiveService(
    private val dailyContentArchiveRepository: DailyContentArchiveRepository
) {
    fun findByDateAndUserId(
        userId: Long,
        date: LocalDate
    ) = dailyContentArchiveRepository.findByDateAndUserId(date, userId)

    fun save(dailyContentArchive: DailyContentArchive): DailyContentArchive = dailyContentArchiveRepository.save(dailyContentArchive)
}
