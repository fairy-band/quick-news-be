package com.nexters.external.service

import com.nexters.external.entity.NewsletterSource
import com.nexters.external.repository.NewsletterSourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NewsletterSourceService(
    private val newsletterSourceRepository: NewsletterSourceRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun save(newsletter: NewsletterSource): NewsletterSource {
        // 중복 체크
        val isDuplicate =
            newsletterSourceRepository.existsBySenderEmailAndSubjectAndReceivedDate(
                newsletter.senderEmail,
                newsletter.subject ?: "",
                newsletter.receivedDate
            )

        if (isDuplicate) {
            logger.warn("Duplicate newsletter found: ${newsletter.subject} from ${newsletter.senderEmail}")
            throw IllegalArgumentException("Duplicate newsletter already exists")
        }

        logger.info("Saving newsletter source: ${newsletter.subject}")
        return newsletterSourceRepository.save(newsletter)
    }

    fun saveAll(newsletters: List<NewsletterSource>): List<NewsletterSource> {
        val uniqueNewsletters =
            newsletters.filter { newsletter ->
                !newsletterSourceRepository.existsBySenderEmailAndSubjectAndReceivedDate(
                    newsletter.senderEmail,
                    newsletter.subject ?: "",
                    newsletter.receivedDate
                )
            }

        logger.info("Saving ${uniqueNewsletters.size} unique newsletters out of ${newsletters.size} total")
        return newsletterSourceRepository.saveAll(uniqueNewsletters)
    }

    fun findById(id: String): NewsletterSource? = newsletterSourceRepository.findById(id).orElse(null)

    fun findByDateRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<NewsletterSource> = newsletterSourceRepository.findByReceivedDateBetween(startDate, endDate)

    fun findBySender(senderEmail: String): List<NewsletterSource> = newsletterSourceRepository.findBySenderEmail(senderEmail)

    fun searchBySubject(keyword: String): List<NewsletterSource> = newsletterSourceRepository.findBySubjectContaining(keyword)

    fun deleteById(id: String) {
        newsletterSourceRepository.deleteById(id)
    }

    fun count(): Long = newsletterSourceRepository.count()

    fun findAll(): List<NewsletterSource> = newsletterSourceRepository.findAll()
}
