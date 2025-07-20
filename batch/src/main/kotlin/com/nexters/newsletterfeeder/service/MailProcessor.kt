package com.nexters.newsletterfeeder.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.service.ContentService
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MailProcessor(
    private val newsletterSourceService: NewsletterSourceService,
    private val contentService: ContentService, // ContentService 주입 추가
    private val newsletterFormatter: NewsletterFormatter,
) {
    fun processEmail(emailMessage: EmailMessage) {
        try {
            logger.info("Processing email from: ${emailMessage.from.joinToString(", ")}")
            logger.info("Subject: ${emailMessage.subject}")
            logger.info("Sent Date: ${emailMessage.sentDate}")

            // 뉴스레터 포맷팅
            val formattedNewsletter = newsletterFormatter.formatNewsletterContent(emailMessage)
            logger.info("Formatted newsletter content type: ${formattedNewsletter.contentType}")
            logger.info("Content length: ${formattedNewsletter.content.length}")

            val newsletterSource = convertToNewsletterSource(formattedNewsletter)
            
            // NewsletterSource 파싱 결과 출력
            printNewsletterSource(newsletterSource)

            // 1. NewsletterSource 저장 (MongoDB)
            val savedNewsletter = newsletterSourceService.save(newsletterSource)
            logger.info("Newsletter saved successfully with ID: ${savedNewsletter.id}")

            // 2. NewsletterSource ID를 참조하여 여러 개의 Content 저장 (PostgreSQL)
            val newsletterSourceId = savedNewsletter.id ?: throw IllegalStateException("NewsletterSource ID is null")
            val contents = convertToContents(formattedNewsletter, newsletterSourceId)
            val savedContents = contentService.saveAll(contents)
            logger.info("Saved ${savedContents.size} contents from newsletter (NewsletterSource ID: $newsletterSourceId)")

            // 3. 저장된 Content 정보 출력
            printSavedContents(savedContents, newsletterSourceId)

        } catch (e: IllegalArgumentException) {
            logger.warn("Newsletter already exists: ${e.message}")
        } catch (e: Exception) {
            logger.error("Error processing email", e)
        }
    }
    
    private fun printNewsletterSource(newsletterSource: NewsletterSource) {
        logger.info("=== NewsletterSource 파싱 결과 ===")
        logger.info("Subject: ${newsletterSource.subject}")
        logger.info("Sender: ${newsletterSource.sender}")
        logger.info("Sender Email: ${newsletterSource.senderEmail}")
        logger.info("Recipient: ${newsletterSource.recipient}")
        logger.info("Recipient Email: ${newsletterSource.recipientEmail}")
        logger.info("Content Type: ${newsletterSource.contentType}")
        logger.info("Received Date: ${newsletterSource.receivedDate}")
        logger.info("Content Length: ${newsletterSource.content.length}")
        logger.info("Content Preview: ${newsletterSource.content.take(200)}...")
        logger.info("Attachments Count: ${newsletterSource.attachments.size}")
        newsletterSource.attachments.forEachIndexed { index, attachment ->
            logger.info("  Attachment $index: ${attachment.filename} (${attachment.contentType}) - ${attachment.size} bytes")
        }
        logger.info("Headers Count: ${newsletterSource.headers.size}")
        newsletterSource.headers.forEach { (key, value) ->
            logger.info("  Header: $key = $value")
        }
        logger.info("=== 파싱 결과 완료 ===")
    }

    private fun printSavedContents(contents: List<Content>, newsletterSourceId: String) {
        logger.info("=== 저장된 Content 목록 (NewsletterSource ID: $newsletterSourceId) ===")
        contents.forEachIndexed { index, content ->
            logger.info("Content ${index + 1}:")
            logger.info("  ID: ${content.id}")
            logger.info("  NewsletterSource ID: ${content.mongoId}")
            logger.info("  Title: ${content.title}")
            logger.info("  Newsletter Name: ${content.newsletterName}")
            logger.info("  Content Length: ${content.content.length}")
            logger.info("  Content Preview: ${content.content.take(100)}...")
            logger.info("  Original URL: ${content.originalUrl}")
            logger.info("  Created At: ${content.createdAt}")
            logger.info("  ---")
        }
        logger.info("=== Content 저장 완료 ===")
    }

    private fun convertToNewsletterSource(formattedNewsletter: FormattedNewsletter): NewsletterSource {
        return NewsletterSource(
            subject = formattedNewsletter.subject,
            sender = formattedNewsletter.sender,
            senderEmail = formattedNewsletter.senderEmail,
            recipient = "newsletter.feeding@gmail.com",
            recipientEmail = "newsletter.feeding@gmail.com",
            content = formattedNewsletter.content,
            contentType = formattedNewsletter.contentType,
            receivedDate = formattedNewsletter.receivedDate ?: LocalDateTime.now(),
            headers = emptyMap(),
            attachments = formattedNewsletter.attachments.map { attachment ->
                com.nexters.external.entity.Attachment(
                    filename = attachment.filename,
                    contentType = attachment.contentType,
                    size = attachment.size
                )
            }
        )
    }

    /**
     * NewsletterSource에서 여러 개의 Content로 변환합니다.
     * mongoId는 NewsletterSource의 ID를 참조합니다.
     */
    private fun convertToContents(formattedNewsletter: FormattedNewsletter, newsletterSourceId: String): List<Content> {
        val contents = mutableListOf<Content>()
        
        // 1. 추출된 아티클들을 Content로 변환
        formattedNewsletter.articles.forEach { article ->
            contents.add(Content(
                id = null, // 명시적으로 null 전달
                mongoId = newsletterSourceId,
                title = article.title,
                content = article.content,
                newsletterName = formattedNewsletter.sender,
                originalUrl = "email://${formattedNewsletter.senderEmail}/${formattedNewsletter.subject}"
            ))
        }
        
        // 2. 아티클이 없는 경우 전체 콘텐츠를 하나의 Content로 저장
        if (contents.isEmpty()) {
            contents.add(Content(
                id = null, // 명시적으로 null 전달
                mongoId = newsletterSourceId,
                title = formattedNewsletter.subject,
                content = formattedNewsletter.content,
                newsletterName = formattedNewsletter.sender,
                originalUrl = "email://${formattedNewsletter.senderEmail}/${formattedNewsletter.subject}"
            ))
        }
        
        logger.info("Converted ${contents.size} contents from newsletter: ${formattedNewsletter.subject} (NewsletterSource ID: $newsletterSourceId)")
        return contents
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MailProcessor::class.java)
        private val emailRegex = "<(.+?)>".toRegex()
    }
}
