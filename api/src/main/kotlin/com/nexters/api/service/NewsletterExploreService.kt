package com.nexters.api.service

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
    ): ExploreContentsResult {
        validateRequest(lastSeenOffset, size)

        val page = findExploreContentPage(lastSeenOffset, size)
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
    ): ExploreContentPageResult =
        when (lastSeenOffset) {
            FIRST_PAGE_OFFSET -> findCachedFirstExploreContentPage(size)
            else -> loadExploreContentPage(lastSeenOffset, size)
        }

    private fun findCachedFirstExploreContentPage(size: Int): ExploreContentPageResult =
        ExploreContentsCache.getFirstPage(size) {
            loadExploreContentPage(FIRST_PAGE_OFFSET, size)
        }

    private fun countExposureContents(): Long =
        ExploreContentsCache.getTotalCount {
            exposureContentService.countAllExposureContents()
        }

    private fun loadExploreContentPage(
        lastSeenOffset: Long,
        size: Int,
    ): ExploreContentPageResult {
        val rows = exposureContentService.getExploreContentRows(lastSeenOffset, size + 1)
        val contents =
            rows
                .take(size)
                .map { it.toResult() }
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
    }
}
