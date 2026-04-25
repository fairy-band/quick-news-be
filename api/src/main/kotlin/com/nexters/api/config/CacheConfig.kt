package com.nexters.api.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.nexters.api.util.LocalCacheValue
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CacheConfig {
    @Bean
    fun caffeineLocalCache(): Cache<String, LocalCacheValue> =
        Caffeine
            .newBuilder()
            .initialCapacity(INITIAL_CAPACITY)
            .maximumSize(MAXIMUM_SIZE)
            .expireAfter(cacheExpiry())
            .build()

    private fun cacheExpiry(): Expiry<String, LocalCacheValue> =
        object : Expiry<String, LocalCacheValue> {
            override fun expireAfterCreate(
                key: String,
                value: LocalCacheValue,
                currentTime: Long,
            ): Long = value.ttl.toNanos()

            override fun expireAfterUpdate(
                key: String,
                value: LocalCacheValue,
                currentTime: Long,
                currentDuration: Long,
            ): Long = value.ttl.toNanos()

            override fun expireAfterRead(
                key: String,
                value: LocalCacheValue,
                currentTime: Long,
                currentDuration: Long,
            ): Long = currentDuration
        }

    companion object {
        private const val INITIAL_CAPACITY = 4
        private const val MAXIMUM_SIZE = 16L
    }
}
