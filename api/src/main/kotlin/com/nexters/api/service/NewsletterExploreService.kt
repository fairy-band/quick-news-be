package com.nexters.api.service

import com.nexters.api.enums.ExploreSortType
import com.nexters.api.util.LocalCache
import com.nexters.external.repository.ExploreContentRow
import com.nexters.external.service.ExposureContentService
import org.springframework.stereotype.Service

@Service
class NewsletterExploreService(
    private val exposureContentService: ExposureContentService,
) {
    fun getExploreContents(
        lastSeenOffset: Long,
        size: Int,
        sortType: ExploreSortType = ExploreSortType.REGISTERED,
    ): ExploreContentsResult {
        validateRequest(lastSeenOffset, size)

        val page = findExploreContentPage(lastSeenOffset, size, sortType)
        val totalCount = countExposureContents()

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
    ): ExploreContentPageResult =
        when (sortType) {
            ExploreSortType.REGISTERED ->
                when (lastSeenOffset) {
                    FIRST_PAGE_OFFSET -> findCachedFirstExploreContentPage(size)
                    else -> loadPage(lastSeenOffset, size, exposureContentService::getExploreContentRows)
                }
            // PUBLISHED 정렬은 첫 페이지도 캐싱하지 않는다.
            // 최신 콘텐츠가 수시로 추가되고, 발행일 기준 정렬을 선택한 사용자에게 오래된 글을 보여주면 의미가 없어지기 때문.
            ExploreSortType.PUBLISHED -> loadPage(lastSeenOffset, size, exposureContentService::getExploreContentRowsSortedByPublishedAt)
        }

    private fun findCachedFirstExploreContentPage(size: Int): ExploreContentPageResult =
        LocalCache.getOrPut(
            key = buildExploreContentPageCacheKey(FIRST_PAGE_OFFSET, size),
            ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
        ) {
            loadPage(FIRST_PAGE_OFFSET, size, exposureContentService::getExploreContentRows)
        }

    private fun countExposureContents(): Long =
        LocalCache.getOrPut(
            key = TOTAL_COUNT_CACHE_KEY,
            ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
        ) {
            exposureContentService.countAllExposureContents()
        }

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

        private fun buildExploreContentPageCacheKey(
            lastSeenOffset: Long,
            size: Int,
        ): String = "$CACHE_KEY_PREFIX:page:last-seen-offset:$lastSeenOffset:size:$size"
    }
}
