package com.nexters.external.service

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.User
import com.nexters.external.repository.DailyContentArchiveRepository
import com.nexters.external.repository.UserExposedContentMappingRepository
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDate

@DataMongoTest
class DailyContentArchiveServiceTest {
    @Autowired
    private lateinit var dailyContentArchiveRepository: DailyContentArchiveRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    private val userExposedContentMappingRepository = mockk<UserExposedContentMappingRepository>(relaxed = true)

    private val service: DailyContentArchiveService
        get() =
            DailyContentArchiveService(
                dailyContentArchiveRepository = dailyContentArchiveRepository,
                userExposedContentMappingRepository = userExposedContentMappingRepository,
                mongoTemplate = mongoTemplate,
            )

    @Test
    fun `findUserIdsByDate should return archive user ids for the date`() {
        val date = LocalDate.of(2026, 6, 17)
        dailyContentArchiveRepository.save(archive(userId = 1L, date = date))
        dailyContentArchiveRepository.save(archive(userId = 2L, date = date))
        dailyContentArchiveRepository.save(archive(userId = 3L, date = date.minusDays(1)))

        val userIds = service.findUserIdsByDate(date)

        assertThat(userIds).containsExactlyInAnyOrder(1L, 2L)
    }

    private fun archive(
        userId: Long,
        date: LocalDate,
    ): DailyContentArchive =
        DailyContentArchive(
            date = date,
            user =
                DailyContentArchive.UserSnapshot.from(
                    User(
                        id = userId,
                        deviceToken = "device-token-$userId",
                    ),
                ),
            exposureContents = emptyList(),
        )
}
