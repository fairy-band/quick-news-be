package com.nexters.api.service

import com.nexters.api.dto.ExploreContentPage
import com.nexters.api.dto.ExposureContentApiResponse
import com.nexters.api.dto.ExposureContentListApiResponse
import com.nexters.api.enums.Language
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
    ): ExposureContentListApiResponse {
        validateRequest(lastSeenOffset, size)

        val page = getExploreContentPage(lastSeenOffset, size)
        val totalCount =
            LocalCache.getOrPut(
                key = TOTAL_COUNT_CACHE_KEY,
                ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
            ) {
                exposureContentService.countAllExposureContents()
            }

        return ExposureContentListApiResponse(
            contents = page.contents,
            totalCount = totalCount,
            hasMore = page.hasMore,
            nextOffset = page.nextOffset,
        )
    }

    private fun getExploreContentPage(
        lastSeenOffset: Long,
        size: Int,
    ): ExploreContentPage =
        if (lastSeenOffset == FIRST_PAGE_OFFSET) {
            LocalCache.getOrPut(
                key = pageCacheKey(lastSeenOffset, size),
                ttl = EXPOSURE_CONTENTS_CACHE_TTL_MINUTES,
            ) {
                loadExploreContentPage(lastSeenOffset, size)
            }
        } else {
            loadExploreContentPage(lastSeenOffset, size)
        }

    private fun loadExploreContentPage(
        lastSeenOffset: Long,
        size: Int,
    ): ExploreContentPage {
        val rows = exposureContentService.getExploreContentRows(lastSeenOffset, size + 1)
        val contents =
            rows
                .take(size)
                .map { it.toApiResponse() }
        val hasMore = rows.size > size

        return ExploreContentPage(
            contents = contents,
            hasMore = hasMore,
            nextOffset = if (hasMore && contents.isNotEmpty()) contents.last().id else null,
        )
    }

    private fun ExploreContentRow.toApiResponse(): ExposureContentApiResponse =
        ExposureContentApiResponse(
            id = id,
            contentId = contentId,
            provocativeKeyword = provocativeKeyword,
            provocativeHeadline = provocativeHeadline,
            summaryContent = summaryContent,
            contentUrl = contentUrl,
            imageUrl = imageUrl,
            newsletterName = newsletterName,
            language = Language.fromString(language),
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
        require(size in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            "size must be between $MIN_PAGE_SIZE and $MAX_PAGE_SIZE"
        }
    }

    companion object {
        private const val MIN_PAGE_SIZE = 1
        private const val MAX_PAGE_SIZE = 50
        private const val FIRST_PAGE_OFFSET = 0L
        private const val EXPOSURE_CONTENTS_CACHE_TTL_MINUTES = 6 * 60L
        private const val CACHE_KEY_PREFIX = "exposure:contents"
        private const val TOTAL_COUNT_CACHE_KEY = "$CACHE_KEY_PREFIX:total-count"

        private fun pageCacheKey(
            lastSeenOffset: Long,
            size: Int,
        ): String = "$CACHE_KEY_PREFIX:page:last-seen-offset:$lastSeenOffset:size:$size"
    }
}
