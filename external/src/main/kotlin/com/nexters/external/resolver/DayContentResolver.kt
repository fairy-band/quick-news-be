package com.nexters.external.resolver

import com.nexters.external.entity.Content
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentService
import org.springframework.stereotype.Service

@Service
class DayContentResolver(
    private val categoryService: CategoryService,
    private val contentService: ContentService,
) {
    fun resolveTodayContents(
        userId: Long,
        categoryId: Long
    ): List<Content> {
        // TODO: 1차 MVP 유저 정보가 필요할지?
        val keywords: List<ReservedKeyword> = categoryService.getKeywordsByCategoryId(categoryId)

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
                    content.reservedKeywords.filter { keyword ->
                        categoryKeywordWeights[keyword] != null
                    }

                // 가중치가 있는 키워드가 없으면 0.0 반환
                if (relevantKeywords.isEmpty()) {
                    0.0
                } else {
                    // 양수 가중치와 음수 가중치를 분리
                    val positiveKeywords =
                        relevantKeywords.filter { keyword ->
                            categoryKeywordWeights[keyword]!! > 0
                        }

                    val negativeKeywords =
                        relevantKeywords.filter { keyword ->
                            categoryKeywordWeights[keyword]!! < 0
                        }

                    // 양수 가중치가 없으면 0.0 반환
                    if (positiveKeywords.isEmpty()) {
                        0.0
                    } else {
                        // 양수 가중치의 곱 계산
                        val positiveWeight =
                            positiveKeywords.fold(1.0) { acc, keyword ->
                                acc * categoryKeywordWeights[keyword]!!
                            }

                        // 음수 가중치의 곱 계산
                        val negativeWeight =
                            negativeKeywords.fold(1.0) { acc, keyword ->
                                acc * categoryKeywordWeights[keyword]!! * -1 // 음수이므로 -1을 곱함
                            }

                        // 최종 가중치 = 양수 가중치의 곱 + 음수 가중치의 합
                        // 음수 가중치가 너무 크면 0으로 만들기 위해 max 사용
                        maxOf(positiveWeight - negativeWeight, 0.0)
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

    /**
     * 카테고리에 설정된 음수 키워드 목록을 가져옵니다.
     */
    fun getNegativeKeywords(categoryId: Long): List<ReservedKeyword> {
        val categoryKeywordWeights = categoryService.getKeywordWeightsByCategoryId(categoryId)

        return categoryKeywordWeights.entries
            .filter { it.value < 0 }
            .map { it.key }
            .toList()
    }

    companion object {
        private const val MAX_CONTENT_SIZE = 6
    }
}
