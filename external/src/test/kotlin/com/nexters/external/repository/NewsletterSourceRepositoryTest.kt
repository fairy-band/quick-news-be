package com.nexters.external.repository

import com.nexters.external.entity.NewsletterSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import java.time.LocalDateTime

@DataMongoTest
class NewsletterSourceRepositoryTest {
    @Autowired
    lateinit var repository: NewsletterSourceRepository

    @Test
    fun `should save and find newsletter`() {
        // given
        val newsletter =
            NewsletterSource(
                subject = "Test Newsletter",
                sender = "Test Sender",
                senderEmail = "test@example.com",
                recipient = "recipient",
                recipientEmail = "recipient@example.com",
                plainText = "Test content",
                htmlText = "<p>Test content</p>",
                contentType = "text/plain",
                receivedDate = LocalDateTime.now(),
            )

        // when
        val actual = repository.save(newsletter)

        // then
        val saved = repository.findById(actual.id!!).get()
        assertEquals("Test Newsletter", saved.subject)
    }

    @Test
    fun `should check duplicate newsletter`() {
        // given
        val date = LocalDateTime.now()
        val newsletter =
            NewsletterSource(
                subject = "Duplicate Test",
                sender = "Sender",
                senderEmail = "duplicate@test.com",
                recipient = "recipient",
                recipientEmail = "recipient@test.com",
                plainText = "Test content",
                htmlText = "<p>Test content</p>",
                contentType = "text/plain",
                receivedDate = date,
            )
        repository.save(newsletter)

        // when
        val actual =
            repository.existsBySenderEmailAndSubjectAndReceivedDate(
                "duplicate@test.com",
                "Duplicate Test",
                date,
            )

        // then
        assertTrue(actual)
    }
}
