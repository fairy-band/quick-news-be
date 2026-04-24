package com.nexters.api.batch.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.support.locks.DefaultLockRegistry
import org.springframework.integration.support.locks.LockRegistry

@Configuration
class BatchLockConfig {
    @Bean
    fun lockRegistry(): LockRegistry = DefaultLockRegistry()
}
