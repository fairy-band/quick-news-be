package com.nexters.api.batch.config

import com.nexters.api.batch.service.PopularNewsletterRankingBatchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class PopularNewsletterRankingScheduler(
    private val popularNewsletterRankingBatchService: PopularNewsletterRankingBatchService,
) {
    private val logger = LoggerFactory.getLogger(PopularNewsletterRankingScheduler::class.java)

    @Scheduled(cron = "0 30 23 * * SUN", zone = "Asia/Seoul")
    fun rebuildGlobalRankingSnapshot() {
        logger.info("인기 뉴스레터 랭킹 스냅샷 스케줄 실행 시작")
        popularNewsletterRankingBatchService.rebuildGlobalRanking()
        logger.info("인기 뉴스레터 랭킹 스냅샷 스케줄 실행 완료")
    }
}
