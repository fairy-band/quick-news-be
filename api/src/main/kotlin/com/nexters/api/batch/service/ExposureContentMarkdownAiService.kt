package com.nexters.api.batch.service

import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.ExposureContentMarkdown
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.ExposureContentMarkdownRepository
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.service.GeminiRateLimiterService
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

            logger.info("Found \${contents.size} unprocessed exposure contents for markdown generation.")

            contents.forEach { exposureContent ->
                try {
                    val prompt = buildMarkdownPrompt(exposureContent.content.content)
                    val response = geminiRateLimiterService.executeTextGeneration(GeminiModel.TWO_FIVE_FLASH, prompt)

                    val markdownText = response?.trim()

                    if (!markdownText.isNullOrEmpty()) {
                        val entity =
                            ExposureContentMarkdown(
                                exposureContentId = exposureContent.id!!,
                                markdownContent = markdownText
                            )
                        exposureContentMarkdownRepository.save(entity)
                        logger.info("Saved markdown for exposure content ID: \${exposureContent.id}")
                    } else {
                        logger.warn("Received empty markdown from AI for exposure content ID: \${exposureContent.id}")
                    }
                } catch (e: RateLimitExceededException) {
                    logger.error("Rate limit exceeded during markdown generation. Halting batch.", e)
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to generate markdown for exposure content ID: \${exposureContent.id}", e)
                }
            }
        } finally {
            isProcessing.set(false)
            logger.debug("Markdown batch processing lock released")
        }
    }

    private fun buildMarkdownPrompt(originalContent: String): String =
        """
        다음 전체 본문을 읽기 좋은 형태의 **한국어** 마크다운 포맷으로 번역 및 재가공해 줘.
        반드시 한국어로 번역 및 작성해야 해.
        불필요한 부연 설명은 제외하고 변환된 마크다운 결과만 응답해 줘.
        
        원본 본문:
        ${"$"}{originalContent}
        """.trimIndent()

    companion object {
        private const val BATCH_SIZE = 5
    }
}
