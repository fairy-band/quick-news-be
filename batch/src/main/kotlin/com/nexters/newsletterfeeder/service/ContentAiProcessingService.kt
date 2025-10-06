package com.nexters.newsletterfeeder.service

import com.nexters.external.repository.ContentRepository
import com.nexters.newsletter.service.NewsletterProcessingService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentAiProcessingService(
    private val contentRepository: ContentRepository,
    private val newsletterProcessingService: NewsletterProcessingService
) {
    private val logger = LoggerFactory.getLogger(ContentAiProcessingService::class.java)

    @Transactional
    fun processUnprocessedContents(): ProcessingResult {
        logger.info("Starting unprocessed content AI processing")

        // Summary가 없는 Content 조회 (10개씩)
        val pageable = PageRequest.of(0, BATCH_SIZE)
        val unprocessedContents = contentRepository.findContentsWithoutSummary(pageable)

        if (unprocessedContents.isEmpty) {
            logger.info("No unprocessed contents found")
            return ProcessingResult(0, 0, 0)
        }

        var processedCount = 0
        var errorCount = 0

        unprocessedContents.content.forEach { content ->
            try {
                // NewsletterProcessingService에 위임하여 처리
                // 요약 생성, 키워드 매핑, ExposureContent 생성을 모두 수행
                newsletterProcessingService.processExistingContent(content)
                processedCount++
                logger.info("Processed content $processedCount/${unprocessedContents.numberOfElements}: ${content.title}")
            } catch (e: Exception) {
                errorCount++
                logger.error("Error processing content ID ${content.id}: ${content.title}", e)
            }
        }

        // 남은 미처리 컨텐츠 수 조회
        val remainingCount = contentRepository.findContentsWithoutSummary(PageRequest.of(0, 1)).totalElements.toInt()

        logger.info("Content AI processing completed. Processed: $processedCount, Errors: $errorCount, Remaining: $remainingCount")

        return ProcessingResult(processedCount, errorCount, remainingCount)
    }

    data class ProcessingResult(
        val processedCount: Int,
        val errorCount: Int,
        val remainingCount: Int
    )

    companion object {
        private const val BATCH_SIZE = 5
    }
}
