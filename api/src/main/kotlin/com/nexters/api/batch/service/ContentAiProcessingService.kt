package com.nexters.api.batch.service

import com.google.gson.JsonSyntaxException
import com.nexters.external.entity.Content
import com.nexters.external.exception.AiProcessingException
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.ContentRepository
import com.nexters.external.service.ContentAnalysisService
import com.nexters.external.service.ExposureContentService
import com.nexters.newsletter.service.NewsletterProcessingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
@Profile("prod")
class ContentAiProcessingService(
    private val contentRepository: ContentRepository,
    private val newsletterProcessingService: NewsletterProcessingService,
    private val contentAnalysisService: ContentAnalysisService,
    private val exposureContentService: ExposureContentService,
) {
    private val logger = LoggerFactory.getLogger(ContentAiProcessingService::class.java)

    // 동시성 제어: 현재 배치 처리 중인지 확인
    private val isProcessing = AtomicBoolean(false)

    /**
     * 미처리 콘텐츠를 배치로 처리합니다.
     * - 배치 크기: 5개
     * - 동시성 제어: 중복 실행 방지
     * - Rate Limit 관리: 1회 API 호출
     */
    fun processUnprocessedContents(): ProcessingResult {
        // 동시성 제어: 이미 처리 중이면 스킵
        if (!isProcessing.compareAndSet(false, true)) {
            logger.warn("Batch processing is already running. Skipping this execution to prevent duplicate processing.")
            return ProcessingResult(0, 0, getRemainingCount())
        }

        try {
            logger.info("Starting unprocessed content AI batch processing")

            // Summary가 없는 Content 조회 (BLOG 우선순위로 정렬됨)
            val pageable = PageRequest.of(0, BATCH_SIZE)
            val unprocessedContentsPage = contentRepository.findContentsWithoutSummaryOrderedByProviderTypePriority(pageable)

            if (unprocessedContentsPage.isEmpty) {
                logger.info("No unprocessed contents found")
                return ProcessingResult(0, 0, 0)
            }

            val contents = unprocessedContentsPage.content
            logger.info("Found ${contents.size} unprocessed contents to process in batch")

            return processContentsBatch(contents)
        } finally {
            // 처리 완료 후 플래그 해제
            isProcessing.set(false)
            logger.debug("Batch processing lock released")
        }
    }

    /**
     * 콘텐츠 배치를 실제로 처리합니다.
     * 1. 콘텐츠 길이 검증
     * 2. AI 배치 분석 (1회 API 호출)
     * 3. ExposureContent 생성
     */
    private fun processContentsBatch(contents: List<Content>): ProcessingResult {
        // 1. 토큰 제한 체크: 콘텐츠 길이 검증
        val validatedContents = validateContentLength(contents)
        if (validatedContents.isEmpty()) {
            logger.warn("All contents exceeded token limit. Skipping batch.")
            return ProcessingResult(0, contents.size, getRemainingCount())
        }

        logContentValidationResult(contents.size, validatedContents.size)

        // 2. 배치 분석 및 ExposureContent 생성
        return try {
            executeBatchProcessing(validatedContents)
        } catch (e: RateLimitExceededException) {
            handleRateLimitException(e)
        } catch (e: Exception) {
            handleBatchProcessingException(e, validatedContents)
        }
    }

    /**
     * 배치 처리를 실행하고 결과를 반환합니다.
     */
    private fun executeBatchProcessing(validatedContents: List<Content>): ProcessingResult {
        logger.info("Processing ${validatedContents.size} contents in single batch API call")

        // AI 배치 분석 (1회 API 호출)
        val batchResults = contentAnalysisService.analyzeBatchAndSave(validatedContents)

        // ExposureContent 생성
        val metrics = createExposureContentsFromBatchResults(validatedContents, batchResults)

        logger.info(
            "Batch completed successfully. " +
                "API calls: 1, Processed: ${metrics.processedCount}/${validatedContents.size}, " +
                "Errors: ${metrics.errorCount}"
        )

        return ProcessingResult(
            processedCount = metrics.processedCount,
            errorCount = metrics.errorCount,
            remainingCount = getRemainingCount()
        )
    }

    /**
     * Rate Limit 초과 예외를 처리합니다.
     */
    private fun handleRateLimitException(e: RateLimitExceededException): Nothing {
        logger.error("Rate limit exceeded. Halting batch without fallback to preserve API quota.", e)
        throw e
    }

    /**
     * 배치 처리 예외를 처리하고 필요시 폴백을 시도합니다.
     */
    private fun handleBatchProcessingException(
        e: Exception,
        validatedContents: List<Content>
    ): ProcessingResult {
        logger.error("Batch processing failed: ${e.message}", e)

        return if (shouldFallbackToIndividual(e)) {
            logger.warn("Attempting fallback to individual processing")
            fallbackToIndividualProcessing(validatedContents)
        } else {
            logger.error("Non-recoverable error. Skipping fallback to preserve API quota.")
            ProcessingResult(0, validatedContents.size, getRemainingCount())
        }
    }

    /**
     * 콘텐츠 검증 결과를 로깅합니다.
     */
    private fun logContentValidationResult(
        originalSize: Int,
        validatedSize: Int
    ) {
        if (validatedSize < originalSize) {
            logger.warn("Filtered out ${originalSize - validatedSize} contents due to length. Processing $validatedSize.")
        }
    }

    /**
     * 배치 분석 결과를 바탕으로 ExposureContent를 생성합니다.
     *
     * @return BatchProcessingMetrics 처리 성공/실패 통계
     */
    private fun createExposureContentsFromBatchResults(
        contents: List<Content>,
        batchResults: Map<String, *>
    ): BatchProcessingMetrics {
        var processedCount = 0
        var errorCount = 0

        contents.forEach { content ->
            try {
                val result = processContentAndCreateExposure(content, batchResults)
                if (result) {
                    processedCount++
                } else {
                    errorCount++
                }
            } catch (e: Exception) {
                errorCount++
                logger.error("Failed to create ExposureContent for content ID ${content.id}: ${content.title}", e)
            }
        }

        return BatchProcessingMetrics(processedCount, errorCount)
    }

    /**
     * 개별 콘텐츠를 처리하고 ExposureContent를 생성합니다.
     *
     * @return Boolean 성공 여부
     */
    private fun processContentAndCreateExposure(
        content: Content,
        batchResults: Map<String, *>
    ): Boolean {
        val contentId = content.id!!.toString()
        val providerType = content.contentProvider?.type?.name ?: "UNKNOWN"

        if (!batchResults.containsKey(contentId)) {
            logger.warn("Content ID $contentId not found in batch results")
            return false
        }

        val summaries = contentAnalysisService.getPrioritizedSummaryByContent(content)
        if (summaries.isEmpty()) {
            logger.warn("No summary found for content (type: $providerType, ID: $contentId): ${content.title}")
            return false
        }

        val latestSummary = summaries.first()
        exposureContentService.createExposureContentFromSummary(latestSummary.id!!)
        logger.info("Processed content (type: $providerType, ID: $contentId): ${content.title}")
        return true
    }

    /**
     * 배치 처리 실패 시 개별 처리로 폴백합니다.
     */
    private fun fallbackToIndividualProcessing(contents: List<Content>): ProcessingResult {
        logger.info("Fallback: processing ${contents.size} contents individually")

        var processedCount = 0
        var errorCount = 0

        contents.forEach { content ->
            try {
                newsletterProcessingService.processExistingContent(content)
                processedCount++
                logger.debug("Processed content ID ${content.id}: ${content.title}")
            } catch (e: RateLimitExceededException) {
                logger.error("Rate limit exceeded during fallback processing", e)
                throw e
            } catch (e: Exception) {
                errorCount++
                logger.error("Failed to process content ID ${content.id}: ${content.title}", e)
            }
        }

        val result = ProcessingResult(processedCount, errorCount, getRemainingCount())
        logger.info("Fallback completed. Processed: $processedCount, Errors: $errorCount, Remaining: ${result.remainingCount}")

        return result
    }

    data class ProcessingResult(
        val processedCount: Int,
        val errorCount: Int,
        val remainingCount: Int
    )

    /**
     * 배치 처리 통계를 담는 데이터 클래스
     */
    private data class BatchProcessingMetrics(
        val processedCount: Int,
        val errorCount: Int
    )

    /**
     * 콘텐츠 길이를 검증하여 토큰 제한 내의 콘텐츠만 반환합니다.
     */
    private fun validateContentLength(contents: List<Content>): List<Content> {
        val validatedContents = mutableListOf<Content>()
        var totalLength = 0

        contents.forEach { content ->
            val contentLength = content.content.length

            when {
                contentLength > MAX_CONTENT_LENGTH -> {
                    logger.warn("Content ID ${content.id} exceeds max length ($contentLength > $MAX_CONTENT_LENGTH). Skipping.")
                }
                (totalLength + contentLength) > MAX_TOTAL_BATCH_LENGTH -> {
                    logger.warn("Batch size limit reached at content ID ${content.id}. Stopping here.")
                    return validatedContents
                }
                else -> {
                    validatedContents.add(content)
                    totalLength += contentLength
                }
            }
        }

        logger.debug("Validated ${validatedContents.size}/${contents.size} contents (total: $totalLength chars)")
        return validatedContents
    }

    /**
     * Fallback 처리 여부를 결정합니다.
     * API 할당량을 보존하기 위해 복구 가능한 오류만 폴백을 시도합니다.
     */
    private fun shouldFallbackToIndividual(exception: Exception): Boolean =
        when (exception) {
            is RateLimitExceededException -> false // API 할당량 보존
            is AiProcessingException -> true // 파싱 실패 등 복구 가능
            is JsonSyntaxException -> true // 개별 처리로 복구 가능
            else -> {
                logger.warn("Unknown exception: ${exception::class.simpleName}. Skipping fallback.")
                false
            }
        }

    private fun getRemainingCount(): Int =
        contentRepository
            .findContentsWithoutSummaryOrderedByProviderTypePriority(
                PageRequest.of(0, 1)
            ).totalElements
            .toInt()

    companion object {
        private const val BATCH_SIZE = 5
        private const val MAX_CONTENT_LENGTH = 10_000 // 콘텐츠당 최대 길이 (약 20K-30K 토큰)
        private const val MAX_TOTAL_BATCH_LENGTH = 50_000 // 배치 전체 최대 길이
    }
}
