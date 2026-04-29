package com.nexters.api.util

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import java.time.Duration

object LocalCache {
    private val localCache: Cache<String, LocalCacheValue> =
        Caffeine
            .newBuilder()
            .initialCapacity(INITIAL_CAPACITY)
            .maximumSize(MAXIMUM_SIZE)
            .expireAfter(cacheExpiry())
            .build()

    fun <T : Any> getOrPut(
        key: String,
        ttl: Long,
        loader: () -> T,
    ): T =
        localCache.getOrPut(
            key = key,
            ttl = ttl,
            loader = loader,
        )

    fun delete(key: String) {
        localCache.invalidate(key)
    }

    fun deleteByPrefix(prefix: String) {
        val keys =
            localCache
                .asMap()
                .keys
                .filter { it.startsWith(prefix) }

        localCache.invalidateAll(keys)
    }

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

    private const val INITIAL_CAPACITY = 4
    private const val MAXIMUM_SIZE = 16L
}

private fun <T : Any> Cache<String, LocalCacheValue>.getOrPut(
    key: String,
    ttl: Long,
    loader: () -> T,
): T {
    require(ttl > 0) {
        "ttlMinutes must be greater than 0"
    }

    val duration = Duration.ofMinutes(ttl)
    val cachedValue =
        get(key) {
            LocalCacheValue(
                value = loader(),
                ttl = duration,
            )
        }.value

    @Suppress("UNCHECKED_CAST")
    return cachedValue as T
}

data class LocalCacheValue(
    val value: Any,
    val ttl: Duration,
)
