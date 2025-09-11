package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.RssProcessingStatus
import com.nexters.external.entity.Summary
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.NewsletterSourceRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.RssProcessingStatusRepository
import com.nexters.external.repository.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Profile("prod", "dev")
class RssAiProcessingService(
    private val rssProcessingStatusRepository: RssProcessingStatusRepository,
    private val newsletterSourceRepository: NewsletterSourceRepository,
    private val contentRepository: ContentRepository,
    private val summaryRepository: SummaryRepository,
    private val summaryService: SummaryService,
    private val keywordService: KeywordService,
    private val exposureContentService: ExposureContentService,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    @Value("\${rss.ai.daily-limit:100}")
    private val dailyLimit: Int = 100
) {
    private val logger = LoggerFactory.getLogger(RssAiProcessingService::class.java)

    @Transactional
    fun processDailyRssWithAi(): ProcessingResult {
        logger.info("Starting RSS AI processing")

        // 오늘 처리한 갯수 확인
        val todayStart = LocalDate.now().atStartOfDay()
        val processedToday = rssProcessingStatusRepository.countProcessedToday(todayStart)

        if (processedToday >= dailyLimit) {
            logger.info("Daily limit reached: $processedToday/$dailyLimit items processed today")
            return ProcessingResult(0, 0, processedToday.toInt(), "Daily limit reached")
        }

        val remainingQuota = dailyLimit - processedToday.toInt()
        logger.info("Processing up to $remainingQuota RSS items (already processed today: $processedToday)")

        // 우선순위가 높은 순서로 미처리 항목 조회
        val pageable = PageRequest.of(0, remainingQuota)
        val unprocessedItems = rssProcessingStatusRepository.findUnprocessedByPriority(pageable)

        var processedCount = 0
        var errorCount = 0

        unprocessedItems.content.forEach { status ->
            try {
                processRssItem(status)
                processedCount++
                logger.info("Processed RSS item $processedCount/$remainingQuota: ${status.title}")
            } catch (e: Exception) {
                errorCount++
                logger.error("Error processing RSS item: ${status.title}", e)
                status.processingError = "AI processing failed: ${e.message}"
                rssProcessingStatusRepository.save(status)
            }
        }

        val totalProcessedToday = processedToday.toInt() + processedCount
        logger.info("RSS AI processing completed. Processed: $processedCount, Errors: $errorCount")

        return ProcessingResult(processedCount, errorCount, totalProcessedToday, "Processing completed")
    }

    private fun processRssItem(status: RssProcessingStatus) {
        // NewsletterSource 조회
        val newsletterSource = newsletterSourceRepository.findById(status.newsletterSourceId).orElse(null)

        if (newsletterSource == null) {
            logger.warn("NewsletterSource not found for ID: ${status.newsletterSourceId}, removing RssProcessingStatus")
            rssProcessingStatusRepository.delete(status)
            return
        }

        // RSS Item URL 가져오기
        val originalUrl = newsletterSource.headers["RSS-Item-URL"] ?: ""

        // NewsletterSource content 가져오기
        val contentText = newsletterSource.content

        // Content 생성 (요약과 키워드는 별도 테이블)
        val content =
            Content(
                newsletterSourceId = newsletterSource.id,
                title = newsletterSource.subject ?: "Untitled",
                content = contentText,
                newsletterName = newsletterSource.sender,
                originalUrl = originalUrl,
                publishedAt = newsletterSource.receivedDate.toLocalDate(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedContent = contentRepository.save(content)

        // 요약 생성 및 저장
        val summaryResult = summaryService.summarize(contentText)

        // 첫 번째 추천 제목 사용 (provocativeHeadlines가 있으면 첫 번째, 없으면 원래 제목)
        val recommendedTitle = summaryResult.provocativeHeadlines.firstOrNull() ?: savedContent.title

        val summary =
            Summary(
                content = savedContent,
                title = recommendedTitle,
                summarizedContent = summaryResult.summary,
                model = summaryResult.usedModel?.modelName ?: "unknown",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        val savedSummary = summaryRepository.save(summary)

        // 키워드 추출 및 실제 키워드만 매핑
        val keywordResult = keywordService.extractKeywords(emptyList(), contentText)

        // matchedKeywords 중에서 실제 ReservedKeyword에 있는 것만 Content에 매핑
        keywordResult.matchedKeywords.forEach { keywordName ->
            val reservedKeyword = reservedKeywordRepository.findByName(keywordName)
            if (reservedKeyword != null) {
                // 이미 매핑되어 있는지 확인
                val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(savedContent, reservedKeyword)
                if (existingMapping == null) {
                    val mapping =
                        ContentKeywordMapping(
                            content = savedContent,
                            keyword = reservedKeyword,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    contentKeywordMappingRepository.save(mapping)
                    logger.debug("Mapped keyword '$keywordName' to content ID: ${savedContent.id}")
                }
            } else {
                logger.debug("Keyword '$keywordName' not found in reserved keywords, skipping mapping")
            }
        }

        // ExposureContent 자동 생성 (노출 확정)
        try {
            val provocativeKeyword =
                keywordResult.matchedKeywords.firstOrNull { keywordName ->
                    reservedKeywordRepository.findByName(keywordName) != null
                } ?: "일반"

            val provocativeHeadline = recommendedTitle

            exposureContentService.createOrUpdateExposureContent(
                content = savedContent,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summaryResult.summary
            )

            logger.info("Created ExposureContent for content ID: ${savedContent.id}")
        } catch (e: Exception) {
            logger.error("Failed to create ExposureContent for content ID: ${savedContent.id}", e)
        }

        // 처리 상태 업데이트
        status.aiProcessed = true
        status.aiProcessedAt = LocalDateTime.now()
        status.contentId = savedContent.id
        rssProcessingStatusRepository.save(status)

        logger.info(
            "Fully processed RSS item: ${savedContent.title} (ID: ${savedContent.id}) - Content, Summary, Keywords, ExposureContent created"
        )
    }

    @Transactional
    fun processRssItemImmediately(status: RssProcessingStatus) {
        logger.info("Starting immediate RSS AI processing for: ${status.title}")
        processRssItem(status)
    }

    fun getProcessingStats(): ProcessingStats {
        val todayStart = LocalDate.now().atStartOfDay()
        val processedToday = rssProcessingStatusRepository.countProcessedToday(todayStart)

        val totalPending =
            rssProcessingStatusRepository
                .findByAiProcessedFalseAndIsProcessedTrue(
                    PageRequest.of(0, 1)
                ).totalElements

        return ProcessingStats(
            processedToday = processedToday.toInt(),
            dailyLimit = dailyLimit,
            pending = totalPending.toInt(),
            remainingQuota = maxOf(0, dailyLimit - processedToday.toInt())
        )
    }

    data class ProcessingResult(
        val processedCount: Int,
        val errorCount: Int,
        val totalProcessedToday: Int,
        val message: String
    )

    data class ProcessingStats(
        val processedToday: Int,
        val dailyLimit: Int,
        val pending: Int,
        val remainingQuota: Int
    )
}
