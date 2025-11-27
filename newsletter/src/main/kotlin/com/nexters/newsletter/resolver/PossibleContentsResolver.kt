package com.nexters.newsletter.resolver

import com.nexters.external.entity.Content
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PossibleContentsResolver(
    private val categoryService: CategoryService,
    private val contentService: ContentService,
) {
    fun resolvePossibleContentsByCategoryIds(
        userId: Long,
        categoryIds: List<Long>,
    ): List<Content> {
        val keywords = categoryService.getKeywordsByCategoryIds(categoryIds)
        val contentsByKeywords = resolvePossibleContentsByKeywords(userId, keywords)

        val contentProviders = categoryService.getContentProvidersByCategoryIds(categoryIds)
        val contentsByProviders = resolvePossibleContentsByProviders(userId, contentProviders)

        // 중복 제거를 위해 Set으로 병합
        val allContents = (contentsByKeywords + contentsByProviders).toSet().toList()

        logger.debug(
            "가능한 콘텐츠 조회 완료. userId: {}, 키워드 수: {}, ContentProvider 수: {}, 총 콘텐츠 수: {}",
            userId,
            keywords.size,
            contentProviders.size,
            allContents.size,
        )

        return allContents
    }

    private fun resolvePossibleContentsByKeywords(
        userId: Long,
        reservedKeywords: List<ReservedKeyword>,
    ): List<Content> {
        val reservedKeywordIds = reservedKeywords.map { it.id!! }
        return contentService.getNotExposedContentsByReservedKeywordIds(userId, reservedKeywordIds)
    }

    private fun resolvePossibleContentsByProviders(
        userId: Long,
        contentProviders: List<*>,
    ): List<Content> {
        val contentProviderIds = contentProviders.mapNotNull { (it as? com.nexters.external.entity.ContentProvider)?.id }
        return if (contentProviderIds.isNotEmpty()) {
            contentService.getNotExposedContentsByContentProviderIds(userId, contentProviderIds)
        } else {
            emptyList()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PossibleContentsResolver::class.java)
    }
}
