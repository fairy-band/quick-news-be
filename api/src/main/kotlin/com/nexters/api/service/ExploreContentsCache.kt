package com.nexters.api.service

import com.nexters.api.util.LocalCache

object ExploreContentsCache {
    fun getFirstPage(
        size: Int,
        loader: () -> ExploreContentPageResult,
    ): ExploreContentPageResult =
        LocalCache.getOrPut(
            key = buildExploreContentPageCacheKey(size),
            ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
            loader = loader,
        )

    fun getTotalCount(loader: () -> Long): Long =
        LocalCache.getOrPut(
            key = TOTAL_COUNT_CACHE_KEY,
            ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
            loader = loader,
        )

    fun evict() {
        LocalCache.delete(TOTAL_COUNT_CACHE_KEY)
        LocalCache.deleteByPrefix(PAGE_CACHE_KEY_PREFIX)
    }

    private fun buildExploreContentPageCacheKey(size: Int): String =
        "${PAGE_CACHE_KEY_PREFIX}last-seen-offset:$FIRST_PAGE_OFFSET:size:$size"

    private const val FIRST_PAGE_OFFSET = 0L
    private const val EXPOSURE_CONTENTS_CACHE_TTL_MINUTES = 6 * 60L
    private const val CACHE_KEY_PREFIX = "exposure:contents"
    private const val TOTAL_COUNT_CACHE_KEY = "$CACHE_KEY_PREFIX:total-count"
    private const val PAGE_CACHE_KEY_PREFIX = "$CACHE_KEY_PREFIX:page:"
}
