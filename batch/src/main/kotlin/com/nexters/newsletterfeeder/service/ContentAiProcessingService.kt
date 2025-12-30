package com.nexters.newsletterfeeder.service

import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.ContentRepository
import com.nexters.newsletter.service.NewsletterProcessingService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class ContentAiProcessingService(
    private val contentRepository: ContentRepository,
    private val newsletterProcessingService: NewsletterProcessingService,
) {
    private val logger = LoggerFactory.getLogger(ContentAiProcessingService::class.java)

    fun processUnprocessedContents(): ProcessingResult {
        logger.info("Starting unprocessed content AI processing")

        // Summary가 없는 Content 조회 (BLOG 우선순위로 정렬됨)
        val pageable = PageRequest.of(0, BATCH_SIZE)
        val unprocessedContents = contentRepository.findContentsWithoutSummaryOrderedByProviderTypePriority(pageable)

        if (unprocessedContents.isEmpty) {
            logger.info("No unprocessed contents found")
            return ProcessingResult(0, 0, 0)
        }

        var processedCount = 0
        var errorCount = 0

        unprocessedContents.content.forEach { content ->
            try {
                val providerType = content.contentProvider?.type?.name ?: "UNKNOWN"
                logger.info("Processing content (type: $providerType): ${content.title}")

                // NewsletterProcessingService에 위임하여 처리
                // 요약 생성, 키워드 매핑, ExposureContent 생성을 모두 수행
                newsletterProcessingService.processExistingContent(content)
                processedCount++
                logger.info(
                    "Processed content $processedCount/${unprocessedContents.numberOfElements} (type: $providerType): ${content.title}"
                )
            } catch (e: RateLimitExceededException) {
                // RateLimitExceededException은 다시 던져서 배치 중단
                logger.error("Rate limit exceeded while processing content ID ${content.id}: ${content.title}", e)
                throw e
            } catch (e: Exception) {
                // 다른 예외는 로깅만 하고 계속 진행
                errorCount++
                logger.error("Error processing content ID ${content.id}: ${content.title}", e)
            }
        }

        // 남은 미처리 컨텐츠 수 조회
        val remainingCount =
            contentRepository
                .findContentsWithoutSummaryOrderedByProviderTypePriority(
                    PageRequest.of(0, 1)
                ).totalElements
                .toInt()

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
