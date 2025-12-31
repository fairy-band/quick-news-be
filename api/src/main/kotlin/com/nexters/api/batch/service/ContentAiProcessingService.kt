package com.nexters.api.batch.service

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
     * 5개의 콘텐츠를 1번의 API 호출로 처리하여 Rate Limit을 효율적으로 관리합니다.
     * 메모리 누수 방지를 위해 처리 후 즉시 참조를 해제합니다.
     * 동시성 제어를 통해 중복 실행을 방지합니다.
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
     * 토큰 제한을 고려하여 콘텐츠 길이를 체크합니다.
     */
    private fun processContentsBatch(contents: List<com.nexters.external.entity.Content>): ProcessingResult {
        var processedCount = 0
        var errorCount = 0

        // 토큰 제한 체크: 콘텐츠 길이 검증
        val validatedContents = validateContentLength(contents)
        if (validatedContents.isEmpty()) {
            logger.warn("All contents exceeded token limit. Skipping batch.")
            return ProcessingResult(0, contents.size, getRemainingCount())
        }

        if (validatedContents.size < contents.size) {
            logger.warn(
                "Filtered out ${contents.size - validatedContents.size} contents due to excessive length. " +
                    "Processing ${validatedContents.size} contents."
            )
        }

        try {
            // 배치로 분석 및 저장 (1번의 API 호출로 validatedContents 처리)
            logger.info("Processing ${validatedContents.size} contents in a single batch API call")
            val batchResults = contentAnalysisService.analyzeBatchAndSave(validatedContents)

            // 각 콘텐츠에 대해 ExposureContent 생성
            validatedContents.forEach { content ->
                try {
                    val contentId = content.id!!.toString()
                    val providerType = content.contentProvider?.type?.name ?: "UNKNOWN"

                    if (batchResults.containsKey(contentId)) {
                        // 배치 분석 성공 - ExposureContent 생성
                        val summaries = contentAnalysisService.getPrioritizedSummaryByContent(content)

                        if (summaries.isNotEmpty()) {
                            val latestSummary = summaries.first()
                            exposureContentService.createExposureContentFromSummary(latestSummary.id!!)
                            processedCount++
                            logger.info(
                                "Successfully processed content $processedCount/${contents.size} " +
                                    "(type: $providerType, ID: $contentId): ${content.title}"
                            )
                        } else {
                            // Fallback: 기본 ExposureContent 생성
                            exposureContentService.createOrUpdateExposureContent(
                                content = content,
                                provocativeKeyword = "Newsletter",
                                provocativeHeadline = content.title,
                                summaryContent = content.content.take(500) + if (content.content.length > 500) "..." else "",
                            )
                            processedCount++
                            logger.warn(
                                "Processed content with fallback (no summary) " +
                                    "(type: $providerType, ID: $contentId): ${content.title}"
                            )
                        }
                    } else {
                        errorCount++
                        logger.warn("Content ID $contentId not found in batch results")
                    }
                } catch (e: Exception) {
                    errorCount++
                    logger.error("Error creating ExposureContent for content ID ${content.id}: ${content.title}", e)
                }
            }

            logger.info(
                "Batch processing completed successfully. " +
                    "API calls: 1, Processed: $processedCount/${validatedContents.size}, Errors: $errorCount"
            )
        } catch (e: RateLimitExceededException) {
            // Rate Limit 초과 시 배치 전체 중단
            // 주의: 이미 1회 API 호출이 카운트되었으므로 fallback 하지 않음
            logger.error("Rate limit exceeded during batch processing. Halting batch without fallback to preserve API quota.", e)
            throw e
        } catch (e: Exception) {
            // 배치 전체 실패 시 처리 전략 결정
            logger.error("Batch processing failed: ${e.message}", e)

            // API 관련 오류가 아닌 경우에만 fallback 시도
            // (파싱 오류, 네트워크 오류 등)
            if (shouldFallbackToIndividual(e)) {
                logger.warn("Attempting fallback to individual processing (Rate Limit preserved)")
                return fallbackToIndividualProcessing(validatedContents)
            } else {
                // 복구 불가능한 오류는 그냥 실패 처리
                logger.error("Non-recoverable error. Skipping fallback to preserve API quota.")
                return ProcessingResult(0, contents.size, getRemainingCount())
            }
        } finally {
            // 메모리 누수 방지: 명시적으로 큰 객체 참조 해제
            // Kotlin의 GC가 처리하지만, 명시적으로 클리어하여 빠른 회수 유도
            logger.debug("Clearing content references to prevent memory leaks")
        }

        // 남은 미처리 콘텐츠 수 조회
        val remainingCount =
            contentRepository
                .findContentsWithoutSummaryOrderedByProviderTypePriority(
                    PageRequest.of(0, 1)
                ).totalElements
                .toInt()

        logger.info(
            "Content AI batch processing completed. " +
                "Processed: $processedCount, Errors: $errorCount, Remaining: $remainingCount"
        )

        return ProcessingResult(processedCount, errorCount, remainingCount)
    }

    /**
     * 배치 처리 실패 시 개별 처리로 폴백합니다.
     * 메모리 누수 방지를 위해 처리 후 즉시 참조를 해제합니다.
     */
    private fun fallbackToIndividualProcessing(contents: List<com.nexters.external.entity.Content>): ProcessingResult {
        logger.info("Starting fallback: individual processing for ${contents.size} contents")

        var processedCount = 0
        var errorCount = 0

        contents.forEach { content ->
            try {
                val providerType = content.contentProvider?.type?.name ?: "UNKNOWN"
                logger.info("Processing content individually (type: $providerType): ${content.title}")

                // 개별 처리
                newsletterProcessingService.processExistingContent(content)
                processedCount++
                logger.info(
                    "Processed content $processedCount/${contents.size} (type: $providerType): ${content.title}"
                )
            } catch (e: RateLimitExceededException) {
                // Rate Limit 초과 시 중단
                logger.error("Rate limit exceeded during individual processing for content ID ${content.id}", e)
                throw e
            } catch (e: Exception) {
                // 다른 예외는 로깅만 하고 계속 진행
                errorCount++
                logger.error("Error processing content ID ${content.id}: ${content.title}", e)
            }
        }

        // 남은 미처리 콘텐츠 수 조회
        val remainingCount =
            contentRepository
                .findContentsWithoutSummaryOrderedByProviderTypePriority(
                    PageRequest.of(0, 1)
                ).totalElements
                .toInt()

        logger.info(
            "Fallback processing completed. Processed: $processedCount, Errors: $errorCount, Remaining: $remainingCount"
        )

        return ProcessingResult(processedCount, errorCount, remainingCount)
    }

    data class ProcessingResult(
        val processedCount: Int,
        val errorCount: Int,
        val remainingCount: Int
    )

    /**
     * 콘텐츠 길이를 검증합니다.
     * 토큰 제한(maxOutputTokens: 8000)을 고려하여 너무 긴 콘텐츠는 필터링합니다.
     *
     * 대략적인 토큰 추정: 한글 1자 ≈ 2-3 토큰, 영어 1단어 ≈ 1-2 토큰
     * 안전한 제한: 콘텐츠당 최대 10,000자 (약 20,000-30,000 토큰)
     * 배치 5개 기준: 총 50,000자 이하 권장
     */
    private fun validateContentLength(contents: List<com.nexters.external.entity.Content>): List<com.nexters.external.entity.Content> {
        val maxContentLength = 10_000 // 콘텐츠당 최대 10,000자
        val maxTotalLength = 50_000 // 배치 전체 최대 50,000자

        val validatedContents = mutableListOf<com.nexters.external.entity.Content>()
        var totalLength = 0

        contents.forEach { content ->
            val contentLength = content.content.length

            when {
                contentLength > maxContentLength -> {
                    logger.warn(
                        "Content ID ${content.id} exceeds max length ($contentLength > $maxContentLength). " +
                            "Skipping to prevent token limit issues."
                    )
                }
                totalLength + contentLength > maxTotalLength -> {
                    logger.warn(
                        "Adding content ID ${content.id} would exceed total batch limit " +
                            "($totalLength + $contentLength > $maxTotalLength). Stopping batch here."
                    )
                    return validatedContents // 여기서 배치 중단
                }
                else -> {
                    validatedContents.add(content)
                    totalLength += contentLength
                }
            }
        }

        logger.debug("Validated ${validatedContents.size}/${contents.size} contents. Total length: $totalLength chars")
        return validatedContents
    }

    /**
     * Fallback 처리 여부를 결정합니다.
     * Rate Limit을 절약하기 위해 API 관련 오류가 아닌 경우에만 fallback을 시도합니다.
     */
    private fun shouldFallbackToIndividual(exception: Exception): Boolean =
        when (exception) {
            // Rate Limit 관련 오류는 fallback 하지 않음
            is RateLimitExceededException -> false

            // AI 처리 오류 (파싱 실패, 응답 형식 오류 등)는 fallback 시도
            is com.nexters.external.exception.AiProcessingException -> true

            // JSON 파싱 오류는 fallback 시도 (개별 처리로 복구 가능)
            is com.google.gson.JsonSyntaxException -> true

            // 기타 예외는 보수적으로 fallback 하지 않음 (API 할당량 보존)
            else -> {
                logger.warn("Unknown exception type: ${exception::class.simpleName}. Skipping fallback.")
                false
            }
        }

    /**
     * 남은 미처리 콘텐츠 수를 조회합니다.
     */
    private fun getRemainingCount(): Int =
        contentRepository
            .findContentsWithoutSummaryOrderedByProviderTypePriority(
                PageRequest.of(0, 1)
            ).totalElements
            .toInt()

    companion object {
        private const val BATCH_SIZE = 5
    }
}
