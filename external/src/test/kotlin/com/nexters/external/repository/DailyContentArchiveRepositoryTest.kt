package com.nexters.external.repository

import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import java.time.LocalDate

@DataMongoTest
class DailyContentArchiveRepositoryTest {
    @Autowired
    lateinit var sut: DailyContentArchiveRepository

    @Test
    fun `should save and find daily content archive`() {
        // given
        val user =
            User(
                id = 1L,
                deviceToken = "test-device-token",
                categories = mutableSetOf(),
                keywords = mutableSetOf()
            )

        val dailyContentArchive =
            DailyContentArchive(
                date = LocalDate.now(),
                user = DailyContentArchive.UserSnapshot.from(user),
                exposureContents = listOf(exposureContentSnapshot())
            )

        // when
        val actual = sut.save(dailyContentArchive)

        // then
        val expected = sut.findById(actual.id!!).get()
        assertEquals(LocalDate.now(), expected.date)
        assertEquals("test-device-token", expected.user.deviceToken)
        assertEquals(
            "Test Newsletter",
            expected.exposureContents
                .first()
                .content.newsletterName
        )
    }

    @Test
    fun `should check exists by user id and date`() {
        // given
        val user =
            User(
                id = 2L,
                deviceToken = "test-device-token-2",
                categories = mutableSetOf(),
                keywords = mutableSetOf()
            )

        val date = LocalDate.now().plusDays(1)
        val dailyContentArchive =
            DailyContentArchive(
                date = date,
                user = DailyContentArchive.UserSnapshot.from(user),
                exposureContents = listOf(exposureContentSnapshot())
            )
        sut.save(dailyContentArchive)

        // when
        val actual = sut.existsByUserIdAndDate(user.id!!, date)

        // then
        assertTrue(actual)
    }

    @Test
    fun `should return false when user id and date not exists`() {
        // given
        val nonExistentUserId = 999L
        val date = LocalDate.now()

        // when
        val actual = sut.existsByUserIdAndDate(nonExistentUserId, date)

        // then
        assertFalse(actual)
    }

    private fun exposureContentSnapshot(): DailyContentArchive.ExposureContentSnapshot =
        DailyContentArchive.ExposureContentSnapshot(
            id = 1L,
            content =
                DailyContentArchive.ContentSnapshot(
                    id = 10L,
                    newsletterName = "Test Newsletter",
                    originalUrl = "https://example.com/test",
                    imageUrl = null,
                    contentProvider = null,
                ),
            provocativeKeyword = "test-keyword",
            provocativeHeadline = "test-headline",
            summaryContent = "test-summary",
            createdAt = java.time.LocalDateTime.now(),
            updatedAt = java.time.LocalDateTime.now(),
        )
}
