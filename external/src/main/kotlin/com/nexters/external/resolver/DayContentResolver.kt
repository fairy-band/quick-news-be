package com.nexters.external.resolver

import com.nexters.external.entity.Content
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentService
import org.springframework.stereotype.Service

@Service
class DayContentResolver(
    val categoryService: CategoryService,
    val contentService: ContentService,
) {
    fun resolveTodayContents(
        userId: Long,
        categoryId: Long
    ): List<Content> {
        // TODO: 1차 MVP 유저 정보가 필요할지?
        val keywords: List<ReservedKeyword> = categoryService.getTodayKeywordsByCategoryId(categoryId)

        val reservedKeywordIds = keywords.map { it.id!! }.toList()
        val possibleContents = contentService.getContentsByReservedKeywordIds(reservedKeywordIds)

        // 1차 오늘 날짜를 우선순위로
        return possibleContents
            .sortedByDescending { it.createdAt }
            .take(MAX_CONTENT_SIZE)
    }

    companion object {
        private const val MAX_CONTENT_SIZE = 6
    }
}
