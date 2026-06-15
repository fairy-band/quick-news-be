package com.nexters.external.service.category

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentCategoryScore
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ContentCategoryScoreRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentProviderCategoryMappingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentCategoryScoreService(
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val categoryRepository: CategoryRepository,
    private val contentProviderCategoryMappingRepository: ContentProviderCategoryMappingRepository,
    private val contentCategoryScoreRepository: ContentCategoryScoreRepository,
) {
    private val logger = LoggerFactory.getLogger(ContentCategoryScoreService::class.java)

    @Transactional
    fun recalculateForContent(content: Content): Int {
        val contentId = content.id
        if (contentId == null) {
            logger.warn("Skipping content category score calculation for unsaved content: {}", content.title)
            return 0
        }

        val keywordIds =
            contentKeywordMappingRepository
                .findByContent(content)
                .mapNotNull { mapping -> mapping.keyword.id }
                .distinct()
        val keywordScoresByCategoryId = calculateKeywordScoresByCategoryId(keywordIds)
        val providerScoresByCategoryId = calculateProviderScoresByCategoryId(content)
        val providerCategoryIds = providerScoresByCategoryId.keys
        val categoryScores = mergeCategoryScores(keywordScoresByCategoryId, providerScoresByCategoryId)
        val calculatedAt = LocalDateTime.now()

        contentCategoryScoreRepository.deleteByContentId(contentId)
        if (categoryScores.isEmpty()) {
            return 0
        }

        val scores =
            categoryScores
                .map { (categoryId, score) ->
                    val competingCategory =
                        categoryScores
                            .filterKeys { otherCategoryId -> otherCategoryId != categoryId }
                            .maxByOrNull { (_, otherScore) -> otherScore.totalScore }
                    val competingCategoryId = competingCategory?.key
                    val competingScore = competingCategory?.value?.totalScore ?: 0.0
                    val providerMismatch = providerCategoryIds.isNotEmpty() && categoryId !in providerCategoryIds
                    val singleCategoryFit =
                        !providerMismatch &&
                            score.totalScore > 0.0 &&
                            (
                                competingScore <= 0.0 ||
                                    score.totalScore >=
                                    competingScore + CategoryFitThresholds.SINGLE_CATEGORY_MIN_SCORE_MARGIN
                            )

                    ContentCategoryScore(
                        contentId = contentId,
                        categoryId = categoryId,
                        keywordScore = score.keywordScore,
                        providerScore = score.providerScore,
                        totalScore = score.totalScore,
                        competingCategoryId = competingCategoryId,
                        competingScore = competingScore,
                        providerMismatch = providerMismatch,
                        singleCategoryFit = singleCategoryFit,
                        calculationVersion = CALCULATION_VERSION,
                        calculatedAt = calculatedAt,
                    )
                }.sortedWith(
                    compareByDescending<ContentCategoryScore> { it.totalScore }
                        .thenBy { it.categoryId },
                )

        contentCategoryScoreRepository.saveAll(scores)
        return scores.size
    }

    private fun calculateKeywordScoresByCategoryId(keywordIds: List<Long>): Map<Long, Double> {
        if (keywordIds.isEmpty()) {
            return emptyMap()
        }

        return categoryRepository
            .findCategoryKeywordMappingByKeywordIds(keywordIds)
            .asSequence()
            .filter { mapping -> mapping.weight > 0.0 }
            .mapNotNull { mapping ->
                mapping.category.id?.let { categoryId -> categoryId to mapping.weight }
            }.groupBy({ (categoryId, _) -> categoryId }, { (_, weight) -> weight })
            .mapValues { (_, weights) -> weights.sum() }
    }

    private fun calculateProviderScoresByCategoryId(content: Content): Map<Long, Double> {
        val providerId = content.contentProvider?.id ?: return emptyMap()

        return contentProviderCategoryMappingRepository
            .findByContentProviderIdIn(listOf(providerId))
            .asSequence()
            .filter { mapping -> mapping.weight > 0.0 }
            .mapNotNull { mapping ->
                mapping.category.id?.let { categoryId ->
                    categoryId to mapping.weight.coerceAtMost(CategoryFitThresholds.PROVIDER_CATEGORY_FIT_WEIGHT_CAP)
                }
            }.groupBy({ (categoryId, _) -> categoryId }, { (_, weight) -> weight })
            .mapValues { (_, weights) -> weights.sum() }
    }

    private fun mergeCategoryScores(
        keywordScoresByCategoryId: Map<Long, Double>,
        providerScoresByCategoryId: Map<Long, Double>,
    ): Map<Long, MutableCategoryScore> {
        val categoryScores = mutableMapOf<Long, MutableCategoryScore>()
        keywordScoresByCategoryId.forEach { (categoryId, score) ->
            categoryScores.getOrPut(categoryId) { MutableCategoryScore() }.keywordScore += score
        }
        providerScoresByCategoryId.forEach { (categoryId, score) ->
            categoryScores.getOrPut(categoryId) { MutableCategoryScore() }.providerScore += score
        }
        return categoryScores
    }

    private data class MutableCategoryScore(
        var keywordScore: Double = 0.0,
        var providerScore: Double = 0.0,
    ) {
        val totalScore: Double
            get() = keywordScore + providerScore
    }

    companion object {
        const val CALCULATION_VERSION = "category-fit-v1"
    }
}
