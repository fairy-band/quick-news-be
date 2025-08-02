package com.nexters.external.resolver

import com.nexters.external.entity.Content
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentService
import com.nexters.external.service.DailyContentArchiveService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DailyContentArchiveResolver(
    private val userService: UserService,
    private val categoryService: CategoryService,
    private val contentService: ContentService,
    private val exposureContentService: ExposureContentService,
    private val dailyContentArchiveService: DailyContentArchiveService,
) {
    fun resolveArbitraryTodayContents(): List<ExposureContent> =
        resolveTodayContents(
            1L,
            categoryService.getAllCategories().random().id!!,
        )

    fun resolveTodayCategoryContents(categoryId: Long): List<ExposureContent> =
        resolveTodayContents(
            1L,
            categoryId,
        )

    fun getTodayContentArchive(
        userId: Long,
        date: LocalDate = LocalDate.now(),
    ): DailyContentArchive? = dailyContentArchiveService.findByDateAndUserId(userId, date)

    @Transactional
    fun resolveTodayContentArchive(
        userId: Long,
        date: LocalDate = LocalDate.now(),
    ): DailyContentArchive {
        val contentArchive = dailyContentArchiveService.findByDateAndUserId(userId, date)

        if (contentArchive != null) {
            return contentArchive
        }

        val user = userService.getUserById(userId)
        val categoryId = user.categories.firstOrNull()?.id ?: categoryService.getAllCategories().random().id!!
        val contents = resolveTodayContents(userId, categoryId)

        val dailyContentArchive =
            DailyContentArchive(
                user = user,
                date = date,
                exposureContents = contents,
            )

        return dailyContentArchiveService.save(dailyContentArchive)
    }

    private fun resolveTodayContents(
        userId: Long,
        categoryId: Long,
    ): List<ExposureContent> {
        // TODO: 1차 MVP 유저 정보가 필요할지?
        val keywords: List<ReservedKeyword> = categoryService.getKeywordsByCategoryId(categoryId)
        val categoryKeywordWeights = categoryService.getKeywordWeightsByCategoryId(categoryId)

        // TODO: entity 의존성 없는 구조로 변경 필요
        val contents =
            resolveTodayContents(
                userId = userId,
                reservedKeywords = keywords,
                keywordWeightsByKeyword = categoryKeywordWeights,
            )

        // Convert Content objects to ExposureContent objects
        return contents.mapNotNull { content -> exposureContentService.getExposureContentByContent(content) }
    }

    private fun resolveTodayContents(
        userId: Long,
        reservedKeywords: List<ReservedKeyword>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    ): List<Content> {
        val reservedKeywordIds = reservedKeywords.map { it.id!! }

        val possibleContents = contentService.getNotExposedContentsByReservedKeywordIds(userId, reservedKeywordIds)

        // 카테고리에 해당하는 키워드 가중치 맵 생성

        val contentWeights =
            possibleContents.associateWith { content ->
                // 컨텐츠의 키워드 중 카테고리-키워드 가중치가 있는 것만 필터링
                val relevantKeywords =
                    content.reservedKeywords.filter { keyword ->
                        keywordWeightsByKeyword[keyword] != null
                    }

                // 가중치가 있는 키워드가 없으면 0.0 반환
                if (relevantKeywords.isEmpty()) {
                    0.0
                } else {
                    // 양수 가중치와 음수 가중치를 분리
                    val positiveKeywords =
                        relevantKeywords.filter { keyword ->
                            keywordWeightsByKeyword[keyword]!! > 0
                        }

                    val negativeKeywords =
                        relevantKeywords.filter { keyword ->
                            keywordWeightsByKeyword[keyword]!! < 0
                        }

                    // 양수 가중치가 없으면 0.0 반환
                    if (positiveKeywords.isEmpty()) {
                        0.0
                    } else {
                        // 양수 가중치의 곱 계산
                        val positiveWeight =
                            positiveKeywords.fold(1.0) { acc, keyword ->
                                acc * keywordWeightsByKeyword[keyword]!!
                            }

                        // 음수 가중치의 곱 계산
                        val negativeWeight =
                            negativeKeywords.fold(1.0) { acc, keyword ->
                                acc * keywordWeightsByKeyword[keyword]!! * -1 // 음수이므로 -1을 곱함
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
