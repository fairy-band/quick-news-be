package com.nexters.api.service

import com.nexters.api.enums.ExploreSortType
import com.nexters.api.util.LocalCache
import com.nexters.external.repository.ExploreContentRow
import com.nexters.external.service.ExposureContentService
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class NewsletterExploreService(
    private val exposureContentService: ExposureContentService,
) {
    fun getExploreContents(
        lastSeenOffset: Long,
        size: Int,
        sortType: ExploreSortType,
        direction: Sort.Direction = Sort.Direction.DESC,
        categoryIds: List<Long>? = null,
    ): ExploreContentsResult {
        validateRequest(lastSeenOffset, size)

        val categoryIds = categoryIds?.takeIf { it.isNotEmpty() }
        val page = findExploreContentPage(lastSeenOffset, size, sortType, direction, categoryIds)
        val totalCount =
            if (categoryIds == null) {
                countExposureContents()
            } else {
                exposureContentService.countByCategoryIds(categoryIds)
            }

        return ExploreContentsResult(
            contents = page.contents,
            totalCount = totalCount,
            hasMore = page.hasMore,
            nextOffset = page.nextOffset,
        )
    }

    private fun findExploreContentPage(
        lastSeenOffset: Long,
        size: Int,
        sortType: ExploreSortType,
        direction: Sort.Direction,
        categoryIds: List<Long>? = null,
    ): ExploreContentPageResult {
        val fetcher: (Long, Int) -> List<ExploreContentRow> =
            when (sortType) {
                ExploreSortType.REGISTERED -> { offset, limit ->
                    exposureContentService.getExploreContentRows(offset, limit, direction, categoryIds)
                }
                ExploreSortType.PUBLISHED -> { offset, limit ->
                    exposureContentService.getExploreContentRowsSortedByPublishedAt(offset, limit, direction, categoryIds)
                }
            }
        return if (categoryIds == null && lastSeenOffset == FIRST_PAGE_OFFSET) {
            findCachedFirstExploreContentPage(sortType, direction, size, fetcher)
        } else {
            loadPage(lastSeenOffset, size, fetcher)
        }
    }

    private fun findCachedFirstExploreContentPage(
        sortType: ExploreSortType,
        direction: Sort.Direction,
        size: Int,
        fetch: (Long, Int) -> List<ExploreContentRow>,
    ): ExploreContentPageResult =
        LocalCache.getOrPut(
            key = buildExploreContentPageCacheKey(sortType, direction, size),
            ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
        ) {
            loadPage(FIRST_PAGE_OFFSET, size, fetch)
        }

    private fun countExposureContents(): Long =
        LocalCache.getOrPut(
            key = TOTAL_COUNT_CACHE_KEY,
            ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
            loader = exposureContentService::countAllExposureContents,
        )

    private fun loadPage(
        lastSeenOffset: Long,
        size: Int,
        fetch: (Long, Int) -> List<ExploreContentRow>,
    ): ExploreContentPageResult {
        val rows = fetch(lastSeenOffset, size + 1)
        val contents = rows.take(size).map { it.toResult() }
        val hasMore = rows.size > size
        return ExploreContentPageResult(
            contents = contents,
            hasMore = hasMore,
            nextOffset = if (hasMore && contents.isNotEmpty()) contents.last().id else null,
        )
    }

    private fun ExploreContentRow.toResult(): ExploreContentResult =
        ExploreContentResult(
            id = id,
            contentId = contentId,
            provocativeKeyword = provocativeKeyword,
            provocativeHeadline = provocativeHeadline,
            summaryContent = summaryContent,
            contentUrl = contentUrl,
            imageUrl = imageUrl,
            newsletterName = newsletterName,
            language = language,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun validateRequest(
        lastSeenOffset: Long,
        size: Int,
    ) {
        require(lastSeenOffset >= 0) {
            "lastSeenOffset must be greater than or equal to 0"
        }
        require(size >= MIN_PAGE_SIZE) {
            "size must be greater than or equal to $MIN_PAGE_SIZE"
        }
    }

    companion object {
        private const val MIN_PAGE_SIZE = 1
        private const val FIRST_PAGE_OFFSET = 0L
        private const val EXPOSURE_CONTENTS_CACHE_TTL_MINUTES = 6 * 60L
        private const val CACHE_KEY_PREFIX = "exposure:contents"
        private const val TOTAL_COUNT_CACHE_KEY = "$CACHE_KEY_PREFIX:total-count"
        private const val PAGE_CACHE_KEY_PREFIX = "$CACHE_KEY_PREFIX:page:"

        private fun buildExploreContentPageCacheKey(
            sortType: ExploreSortType,
            direction: Sort.Direction,
            size: Int,
        ): String = "${PAGE_CACHE_KEY_PREFIX}sort:${sortType.name}:direction:${direction.name}:size:$size"
    }
}
