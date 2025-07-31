package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
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
                deviceToken = "test-device-token",
                categories = mutableSetOf(),
                keywords = mutableSetOf()
            )

        val exposureContent =
            ExposureContent(
                content =
                    Content(
                        title = "Test Content Title",
                        content = "Test content body",
                        newsletterName = "Test Newsletter",
                        originalUrl = "https://example.com/test",
                        publishedAt = LocalDate.now()
                    ),
                provocativeKeyword = "test-keyword",
                provocativeHeadline = "test-headline",
                summaryContent = "test-summary"
            )

        val dailyContentArchive =
            DailyContentArchive(
                date = LocalDate.now(),
                user = user,
                exposureContents = listOf(exposureContent)
            )

        // when
        val actual = sut.save(dailyContentArchive)

        // then
        val expected = sut.findById(actual.id!!).get()
        assertEquals(LocalDate.now(), expected.date)
        assertEquals("test-device-token", expected.user.deviceToken)
        assertEquals(
            "Test Content Title",
            expected.exposureContents
                .first()
                .content.title
        )
    }

    @Test
    fun `should check exists by user id and date`() {
        // given
        val user =
            User(
                deviceToken = "test-device-token-2",
                categories = mutableSetOf(),
                keywords = mutableSetOf()
            )

        val content =
            Content(
                title = "Test Content Title 2",
                content = "Test content body 2",
                newsletterName = "Test Newsletter 2",
                originalUrl = "https://example.com/test2",
                publishedAt = LocalDate.now()
            )

        val exposureContent =
            ExposureContent(
                content = content,
                provocativeKeyword = "test-keyword-2",
                provocativeHeadline = "test-headline-2",
                summaryContent = "test-summary-2"
            )

        val date = LocalDate.now()
        val dailyContentArchive =
            DailyContentArchive(
                date = date,
                user = user,
                exposureContents = listOf(exposureContent)
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
}
