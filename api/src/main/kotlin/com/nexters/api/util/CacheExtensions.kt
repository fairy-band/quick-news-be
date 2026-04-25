package com.nexters.api.util

import com.github.benmanes.caffeine.cache.Cache
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class LocalCache(
    cache: Cache<String, LocalCacheValue>,
) {
    init {
        localCache = cache
    }

    companion object {
        private lateinit var localCache: Cache<String, LocalCacheValue>

        fun <T : Any> getOrPut(
            key: String,
            ttl: Long,
            loader: () -> T,
        ): T =
            if (Companion::localCache.isInitialized) {
                localCache.getOrPut(
                    key = key,
                    ttl = ttl,
                    loader = loader,
                )
            } else {
                loader()
            }
    }
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
