package com.nexters.api.batch.service

import com.google.gson.JsonSyntaxException
import com.nexters.external.constants.ContentConstants.MAX_CONTENT_LENGTH
import com.nexters.external.constants.ContentConstants.MAX_TOTAL_BATCH_LENGTH
import com.nexters.external.entity.Content
import com.nexters.external.exception.AiProcessingException
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.filter.AdAndPromotionalFilter
import com.nexters.external.filter.FilterChain
import com.nexters.external.repository.ContentRepository
import com.nexters.external.service.ContentAnalysisService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.dto.GeminiModel
import com.nexters.external.enums.ContentProcessingStage
import com.nexters.external.apiclient.EmbeddingServiceClient
import com.nexters.external.entity.ExposureContentMarkdown
import com.nexters.external.repository.ExposureContentMarkdownRepository
import com.nexters.external.service.GeminiRateLimiterService
import com.nexters.external.service.ContentProcessingStateService
import com.nexters.external.support.MarkdownValidator
import com.nexters.newsletter.service.NewsletterProcessingService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ContentAiProcessingService(
    private val contentRepository: ContentRepository,
    private val newsletterProcessingService: NewsletterProcessingService,
    private val contentAnalysisService: ContentAnalysisService,
    private val exposureContentService: ExposureContentService,
    private val geminiRateLimiterService: GeminiRateLimiterService,
    private val exposureContentMarkdownRepository: ExposureContentMarkdownRepository,
    private val embeddingServiceClient: EmbeddingServiceClient,
    private val processingStateService: ContentProcessingStateService,
) {
    private val logger = LoggerFactory.getLogger(ContentAiProcessingService::class.java)

    @org.springframework.beans.factory.annotation.Value("\${batch.content.buffer-hours:2}")
    private var bufferHours: Long = 2

    // 동시성 제어: 현재 배치 처리 중인지 확인
    private val isProcessing = AtomicBoolean(false)

    // 필터 체인 설정
    private val filterChain =
        FilterChain
            .builder()
            .addFilter(AdAndPromotionalFilter())
            .addLengthFilter(
                minLength = MIN_CONTENT_LENGTH,
                maxLength = MAX_CONTENT_LENGTH
            ).build()

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
            val createdBefore = java.time.LocalDateTime.now().minusHours(bufferHours)
            logger.info("Starting unprocessed content AI batch processing (createdBefore: $createdBefore, bufferHours: $bufferHours)")

            // Summary가 없는 Content 조회 (BLOG 우선순위 + 카테고리 균형 고려, 2시간 이상 지난 콘텐츠 대상)
            val unprocessedContents =
                contentRepository.findContentsWithoutSummaryOrderedByCategoryBalance(
                    minLength = MIN_CONTENT_LENGTH,
                    maxLength = MAX_CONTENT_LENGTH,
                    limit = BATCH_SIZE * CANDIDATE_FETCH_FACTOR,
                    createdBefore = createdBefore,
                )

            val eligibleContents = processingStateService.eligible(unprocessedContents, ContentProcessingStage.AI).take(BATCH_SIZE)
            if (eligibleContents.isEmpty()) {
                logger.info("No unprocessed contents found in batch size $BATCH_SIZE, trying extended search with MAX_TOTAL_BATCH_LENGTH")
                return processSingleLargeContent()
            }

            logger.info("Found ${eligibleContents.size} eligible contents to process in batch")

            return processContentsBatch(eligibleContents)
        } finally {
            // 처리 완료 후 플래그 해제
            isProcessing.set(false)
            logger.debug("Batch processing lock released")
        }
    }

    /**
     * 배치에서 콘텐츠를 찾지 못했을 때 MAX_TOTAL_BATCH_LENGTH 길이의 콘텐츠 1개만 처리합니다.
     */
    private fun processSingleLargeContent(): ProcessingResult {
        logger.info("Attempting to process single content with length up to $MAX_TOTAL_BATCH_LENGTH")

        try {
            // MAX_TOTAL_BATCH_LENGTH로 범위를 넓혀서 콘텐츠 1개 조회 (bufferHours 적용)
            val createdBefore = java.time.LocalDateTime.now().minusHours(bufferHours)
            val unprocessedContents =
                contentRepository.findContentsWithoutSummaryOrderedByCategoryBalance(
                    minLength = MIN_CONTENT_LENGTH,
                    maxLength = MAX_TOTAL_BATCH_LENGTH,
                    limit = CANDIDATE_FETCH_FACTOR,
                    createdBefore = createdBefore,
                )

            if (unprocessedContents.isEmpty()) {
                logger.info("No unprocessed contents found even with MAX_TOTAL_BATCH_LENGTH")
                return ProcessingResult(0, 0, 0)
            }

            val singleContent = processingStateService.eligible(unprocessedContents, ContentProcessingStage.AI).firstOrNull()
                ?: return ProcessingResult(0, 0, getRemainingCount())
            logger.info("Processing single content (ID: ${singleContent.id}, length: ${singleContent.content.length})")
            return processContentsBatch(listOf(singleContent))
        } catch (e: Exception) {
            logger.error("Failed to process single large content", e)
            return ProcessingResult(0, 1, getRemainingCount())
        }
    }

    /**
     * 콘텐츠 배치를 실제로 처리합니다.
     * 1. 필터 체인 적용 (길이, provider type, 날짜 등)
     * 2. 토큰 제한 체크
     * 3. AI 배치 분석 (1회 API 호출)
     * 4. ExposureContent 생성
     */
    private fun processContentsBatch(contents: List<Content>): ProcessingResult {
        // 1. 필터 체인 적용
        val filteredContents = filterChain.filter(contents)
        if (filteredContents.isEmpty()) {
            logger.warn("All contents filtered out by filter chain. Skipping batch.")
            return ProcessingResult(0, contents.size, getRemainingCount())
        }

        // 2. 토큰 제한 체크: 배치 전체 길이 검증
        val validatedContents = validateBatchLength(filteredContents)
        if (validatedContents.isEmpty()) {
            logger.warn("All contents exceeded batch token limit. Skipping batch.")
            return ProcessingResult(0, filteredContents.size, getRemainingCount())
        }

        logContentValidationResult(filteredContents.size, validatedContents.size)

        // 3. 배치 분석 및 ExposureContent 생성
        return try {
            executeBatchProcessing(validatedContents)
        } catch (e: RateLimitExceededException) {
            handleRateLimitException(e, validatedContents)
        } catch (e: Exception) {
            handleBatchProcessingException(e, validatedContents)
        }
    }

    /**
     * 배치 처리를 실행하고 결과를 반환합니다.
     */
    private fun executeBatchProcessing(validatedContents: List<Content>): ProcessingResult {
        logger.info("Processing ${validatedContents.size} contents in single batch API call")
        processingStateService.processing(validatedContents, ContentProcessingStage.AI)

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
    private fun handleRateLimitException(e: RateLimitExceededException, contents: List<Content>): Nothing {
        logger.error("Rate limit exceeded. Halting batch without fallback to preserve API quota.", e)
        processingStateService.retry(contents, ContentProcessingStage.AI, e.retryAt, e)
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
                    processingStateService.ready(content.id!!, ContentProcessingStage.AI)
                } else {
                    errorCount++
                    processingStateService.retry(listOf(content), ContentProcessingStage.AI, null, IllegalStateException("AI batch result was incomplete"))
                }
            } catch (e: Exception) {
                errorCount++
                processingStateService.retry(listOf(content), ContentProcessingStage.AI, null, e)
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
        val exposureContent = exposureContentService.createExposureContentFromSummary(latestSummary.id!!)
        processingStateService.processing(listOf(content), ContentProcessingStage.MARKDOWN)
        
        // 마크다운 즉시 생성 및 저장 (가드레일 검증 및 완결성 보장)
        try {
            val originalContent = exposureContent.content.content
            val originalUrl = exposureContent.content.originalUrl

            var markdownText = geminiRateLimiterService.executeMarkdownGeneration(
                GeminiModel.TWO_FIVE_FLASH,
                originalContent,
                originalUrl
            )?.trim()

            // 가드레일: 완결성 검증 실패 시 1회 재시도
            if (!MarkdownValidator.isValid(markdownText)) {
                logger.warn("Immediate markdown validation failed for exposureContentId=${exposureContent.id} (length=${markdownText?.length ?: 0}). Retrying once...")
                markdownText = geminiRateLimiterService.executeMarkdownGeneration(
                    GeminiModel.TWO_FIVE_FLASH,
                    originalContent,
                    originalUrl
                )?.trim()
            }

            if (!markdownText.isNullOrEmpty()) {
                val finalMarkdown = MarkdownValidator.ensureSourceLink(markdownText, originalUrl)
                val existing = exposureContentMarkdownRepository.findByExposureContentId(exposureContent.id!!)
                val markdownEntity = if (existing != null) {
                    ExposureContentMarkdown(
                        id = existing.id,
                        exposureContentId = existing.exposureContentId,
                        markdownContent = finalMarkdown,
                        createdAt = existing.createdAt,
                        updatedAt = java.time.LocalDateTime.now()
                    )
                } else {
                    ExposureContentMarkdown(
                        exposureContentId = exposureContent.id!!,
                        markdownContent = finalMarkdown
                    )
                }
                exposureContentMarkdownRepository.save(markdownEntity)
                processingStateService.ready(content.id!!, ContentProcessingStage.MARKDOWN)
                logger.info("Saved AI-generated markdown immediately for exposure content ID: ${exposureContent.id} (length=${finalMarkdown.length})")
            } else {
                processingStateService.retry(listOf(content), ContentProcessingStage.MARKDOWN, null, IllegalStateException("Markdown generation returned empty content"))
            }
        } catch (e: Exception) {
            processingStateService.retry(listOf(content), ContentProcessingStage.MARKDOWN, (e as? RateLimitExceededException)?.retryAt, e)
            logger.error("Failed to generate immediate markdown for exposure content ID: ${exposureContent.id}", e)
        }

        // 임베딩 파이프라인 연동: 제목, 헤드라인, 키워드, 요약, 마크다운 기반 bge-m3 pgvector 적재
        try {
            embeddingServiceClient.embedContent(content.id!!)
        } catch (e: Exception) {
            logger.warn("Failed to generate embedding for content ID ${content.id}: ${e.message}")
        }

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
     * 배치 전체 길이를 검증하여 토큰 제한 내의 콘텐츠만 반환합니다.
     */
    private fun validateBatchLength(contents: List<Content>): List<Content> {
        val validatedContents = mutableListOf<Content>()
        var totalLength = 0

        contents.forEach { content ->
            val contentLength = content.content.length

            if ((totalLength + contentLength) > MAX_TOTAL_BATCH_LENGTH) {
                logger.warn("Batch size limit reached at content ID ${content.id}. Stopping here.")
                return validatedContents
            }

            validatedContents.add(content)
            totalLength += contentLength
        }

        logger.debug("Validated ${validatedContents.size}/${contents.size} contents (total: $totalLength chars)")
        return validatedContents
    }

    /**
     * 콘텐츠 길이를 검증하여 토큰 제한 내의 콘텐츠만 반환합니다.
     * @deprecated 필터 체인으로 대체됨. 하위 호환성을 위해 유지
     */
    @Deprecated("Use FilterChain instead")
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
            .countContentsWithoutSummaryInLengthRange(
                minLength = MIN_CONTENT_LENGTH,
                maxLength = MAX_CONTENT_LENGTH,
            ).toInt()

    companion object {
        private const val BATCH_SIZE = 5
        private const val CANDIDATE_FETCH_FACTOR = 3
        private const val MIN_CONTENT_LENGTH = 500 // 최소 500자
    }
}
