package com.nexters.api.batch.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class SchedulingConfig {
    private val logger = LoggerFactory.getLogger(SchedulingConfig::class.java)

    init {
        logger.info("Batch scheduling is ENABLED")
    }
}
