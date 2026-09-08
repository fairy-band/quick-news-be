package com.nexters.api.batch.service

import com.nexters.external.apiclient.EmbeddingServiceClient
import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.ExposureContentMarkdown
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.ExposureContentMarkdownRepository
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.service.GeminiRateLimiterService
import com.nexters.external.support.MarkdownValidator
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ExposureContentMarkdownAiService(
    private val exposureContentRepository: ExposureContentRepository,
    private val exposureContentMarkdownRepository: ExposureContentMarkdownRepository,
    private val geminiRateLimiterService: GeminiRateLimiterService,
    private val embeddingServiceClient: EmbeddingServiceClient,
) {
    private val logger = LoggerFactory.getLogger(ExposureContentMarkdownAiService::class.java)
    private val isProcessing = AtomicBoolean(false)

    fun processUnprocessedMarkdowns() {
        if (!isProcessing.compareAndSet(false, true)) {
            logger.warn("Markdown batch processing is already running. Skipping this execution.")
            return
        }

        try {
            logger.info("Starting unprocessed markdown AI batch processing")

            // 마크다운이 없는 항목을 조회
            val contents = exposureContentRepository.findExposureContentsWithoutMarkdown(PageRequest.of(0, BATCH_SIZE))

            if (contents.isEmpty()) {
                logger.info("No unprocessed exposure contents found for markdown generation.")
                return
            }

            logger.info("Found ${contents.size} unprocessed exposure contents for markdown generation.")

            contents.forEach { exposureContent ->
                try {
                    val originalContent = exposureContent.content.content
                    val originalUrl = exposureContent.content.originalUrl
                    var response = geminiRateLimiterService.executeMarkdownGeneration(GeminiModel.TWO_FIVE_FLASH, originalContent, originalUrl)
                    var markdownText = response?.trim()

                    // 가드레일: 마크다운 구조 및 완결성 검증 실패 시 1회 재시도
                    if (!MarkdownValidator.isValid(markdownText)) {
                        logger.warn("Markdown validation failed for exposureContentId=${exposureContent.id} (length=${markdownText?.length ?: 0}). Retrying once...")
                        response = geminiRateLimiterService.executeMarkdownGeneration(GeminiModel.TWO_FIVE_FLASH, originalContent, originalUrl)
                        markdownText = response?.trim()
                    }

                    if (!markdownText.isNullOrEmpty()) {
                        val finalMarkdown = MarkdownValidator.standardizeMarkdown(markdownText, originalUrl)
                        val existing = exposureContentMarkdownRepository.findByExposureContentId(exposureContent.id!!)
                        val entity = if (existing != null) {
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
                        exposureContentMarkdownRepository.save(entity)
                        logger.info("Saved AI-generated markdown for exposure content ID: ${exposureContent.id} (length=${finalMarkdown.length})")

                        // 임베딩 파이프라인 연동: 마크다운이 추가/보강된 최신 텍스트로 bge-m3 임베딩 갱신
                        try {
                            embeddingServiceClient.embedContent(exposureContent.content.id!!)
                        } catch (e: Exception) {
                            logger.warn("Failed to update embedding for content ID ${exposureContent.content.id}: ${e.message}")
                        }
                    } else {
                        logger.warn("Received empty markdown from AI for exposure content ID: ${exposureContent.id}")
                    }
                } catch (e: RateLimitExceededException) {
                    logger.error("Rate limit exceeded during markdown generation. Halting batch.", e)
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to generate markdown for exposure content ID: ${exposureContent.id}", e)
                }
            }
        } finally {
            isProcessing.set(false)
            logger.debug("Markdown batch processing lock released")
        }
    }

    companion object {
        private const val BATCH_SIZE = 5
    }
}
