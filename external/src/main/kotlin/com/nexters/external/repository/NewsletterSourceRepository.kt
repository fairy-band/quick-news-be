package com.nexters.external.repository

import com.nexters.external.entity.NewsletterSource
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface NewsletterSourceRepository : MongoRepository<NewsletterSource, String> {
    fun findByReceivedDateBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<NewsletterSource>

    fun findBySenderEmail(senderEmail: String): List<NewsletterSource>

    fun findBySubjectContaining(keyword: String): List<NewsletterSource>

    fun existsBySenderEmailAndSubjectAndReceivedDate(
        senderEmail: String,
        subject: String,
        receivedDate: LocalDateTime
    ): Boolean
}
