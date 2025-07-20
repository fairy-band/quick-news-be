package com.nexters.newsletterfeeder.scheduler

import com.nexters.newsletterfeeder.service.MailReader
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledMailReader(
    val mailReader: MailReader
) {
    // 매일 아침 8시에 실행 (cron: 초 분 시 일 월 요일)
    @Scheduled(cron = "0 08 21 * * *")
    fun triggerMorningSchedule() {
        LOGGER.info("Starting scheduled email reading at 8:00 AM")
        mailReader.read()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ScheduledMailReader::class.java)
    }
}
