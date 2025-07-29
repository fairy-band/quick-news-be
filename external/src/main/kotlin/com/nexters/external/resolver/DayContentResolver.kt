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

        // 카테고리에 해당하는 키워드 가중치 맵 생성
        val categoryKeywordWeights = categoryService.getKeywordWeightsByCategoryId(categoryId)

        // 컨텐츠별 가중치 계산
        // TODO: entity 의존성 없는 구조로 변경 필요
        val contentWeights =
            possibleContents.associateWith { content ->
                // 컨텐츠의 키워드 중 카테고리-키워드 가중치가 있는 것만 필터링
                val relevantKeywords =
                    content.reservedKeywords
                        .filter { keyword -> categoryKeywordWeights[keyword] != null && categoryKeywordWeights[keyword]!! > 0 }

                // 가중치가 있는 키워드가 없으면 0.0 반환
                if (relevantKeywords.isEmpty()) {
                    0.0
                } else {
                    // 가중치의 곱 계산
                    relevantKeywords.fold(1.0) { acc, keyword ->
                        acc * (categoryKeywordWeights[keyword] ?: 1.0)
                    }
                }
            }

        // 가중치가 0인 컨텐츠 필터링하고 가중치 내림차순으로 정렬
        return contentWeights
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
            .take(MAX_CONTENT_SIZE)
    }

    companion object {
        private const val MAX_CONTENT_SIZE = 6
    }
}
