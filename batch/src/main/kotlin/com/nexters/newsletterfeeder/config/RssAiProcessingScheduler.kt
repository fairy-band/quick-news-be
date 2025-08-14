package com.nexters.newsletterfeeder.config

import com.nexters.external.service.RssAiProcessingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
@Profile("prod", "dev")
class RssAiProcessingScheduler(
    private val rssAiProcessingService: RssAiProcessingService
) {
    private val logger = LoggerFactory.getLogger(RssAiProcessingScheduler::class.java)

    @Scheduled(cron = "0 0 23 * * *") // 매일 오후 11시에 실행
    fun processRssWithAi() {
        logger.info("Starting RSS AI processing scheduler")
        val result = rssAiProcessingService.processDailyRssWithAi()
        logger.info("RSS AI processing completed: $result")
    }

    @Scheduled(cron = "0 0 12 * * *") // 매일 정오에 실행
    fun logProcessingStats() {
        val stats = rssAiProcessingService.getProcessingStats()
        logger.info("RSS AI Processing Stats - Processed today: ${stats.processedToday}/${stats.dailyLimit}, Pending: ${stats.pending}")
    }
}
