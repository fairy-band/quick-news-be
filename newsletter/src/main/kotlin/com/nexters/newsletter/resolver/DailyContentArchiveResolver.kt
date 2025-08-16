package com.nexters.newsletter.resolver

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
    private val calculator = RecommendScoreCalculator()

    fun resolveTodayContents(userId: Long): List<ExposureContent> {
        val userCategories = categoryService.getCategoriesByUserId(userId)

        val categories =
            if (userCategories.isEmpty()) {
                categoryService.getAllCategories()
            } else {
                userCategories
            }

        return resolveTodayContents(
            userId,
            categories.map { it.id!! },
        )
    }

    fun resolveArbitraryTodayContents(): List<ExposureContent> =
        resolveTodayContents(
            ARBITRARY_USER_ID,
            categoryService.getAllCategories().map { it.id!! },
        )

    fun resolveTodayCategoryContents(categoryId: Long): List<ExposureContent> =
        resolveTodayContents(
            ARBITRARY_USER_ID,
            listOf(categoryId),
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
        val userCategories = user.categories.map { it.id!! }
        val categoryIds =
            if (userCategories.isNotEmpty()) {
                userCategories
            } else {
                categoryService.getAllCategories().map { it.id!! }
            }

        val contents = resolveTodayContents(userId, categoryIds)

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
        categoryIds: List<Long>,
    ): List<ExposureContent> {
        // TODO: 1차 MVP 유저 정보가 필요할지?
        val keywords: List<ReservedKeyword> = categoryService.getKeywordsByCategoryIds(categoryIds)
        val categoryKeywordWeights = categoryService.getKeywordWeightsByCategoryIds(categoryIds)

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

                val positiveKeywords =
                    relevantKeywords.filter { keyword ->
                        keywordWeightsByKeyword[keyword]!! > 0
                    }

                val negativeKeywords =
                    relevantKeywords.filter { keyword ->
                        keywordWeightsByKeyword[keyword]!! < 0
                    }

                calculator
                    .calculate(
                        RecommendCalculateSource(
                            positiveKeywords.map { PositiveKeywordSource(keywordWeightsByKeyword[it] ?: 0.0) },
                            negativeKeywords.map { NegativeKeywordSource(keywordWeightsByKeyword[it] ?: 0.0) },
                            content.publishedAt
                        ),
                    ).recommendScore
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
    fun getNegativeKeywords(categoryIds: List<Long>): List<ReservedKeyword> {
        val categoryKeywordWeights = categoryService.getKeywordWeightsByCategoryIds(categoryIds)

        return categoryKeywordWeights.entries
            .filter { it.value < 0 }
            .map { it.key }
            .toList()
    }

    companion object {
        private const val MAX_CONTENT_SIZE = 6
        private const val ARBITRARY_USER_ID = 1L // 임시로 사용되는 유저 ID
    }
}
