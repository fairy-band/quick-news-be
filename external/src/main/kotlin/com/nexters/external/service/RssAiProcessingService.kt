package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.RssProcessingStatus
import com.nexters.external.entity.Summary
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.NewsletterSourceRepository
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
@Profile("prod")
class RssAiProcessingService(
    private val rssProcessingStatusRepository: RssProcessingStatusRepository,
    private val newsletterSourceRepository: NewsletterSourceRepository,
    private val contentRepository: ContentRepository,
    private val summaryRepository: SummaryRepository,
    private val summaryService: SummaryService,
    private val keywordService: KeywordService,
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
        val newsletterSource =
            newsletterSourceRepository
                .findById(status.newsletterSourceId)
                .orElseThrow { NoSuchElementException("NewsletterSource not found: ${status.newsletterSourceId}") }

        // RSS Item URL 가져오기
        val originalUrl = newsletterSource.headers["RSS-Item-URL"] ?: ""

        // Content 생성 (요약과 키워드는 별도 테이블)
        val content =
            Content(
                newsletterSourceId = newsletterSource.id,
                title = newsletterSource.subject ?: "Untitled",
                content = newsletterSource.content,
                newsletterName = newsletterSource.sender,
                originalUrl = originalUrl,
                publishedAt = newsletterSource.receivedDate.toLocalDate(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedContent = contentRepository.save(content)

        // 요약 생성 및 저장
        val summaryResult = summaryService.summarize(newsletterSource.content)
        val summary =
            Summary(
                content = savedContent,
                title = savedContent.title,
                summarizedContent = summaryResult.summary,
                model = summaryResult.usedModel?.modelName ?: "unknown",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        summaryRepository.save(summary)

        // 키워드 추출 및 저장 (KeywordService는 이미 CandidateKeyword에 저장함)
        keywordService.extractKeywords(emptyList(), newsletterSource.content)

        // 처리 상태 업데이트
        status.aiProcessed = true
        status.aiProcessedAt = LocalDateTime.now()
        status.contentId = savedContent.id
        rssProcessingStatusRepository.save(status)

        logger.debug("Created content from RSS: ${content.title} (ID: ${savedContent.id})")
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
