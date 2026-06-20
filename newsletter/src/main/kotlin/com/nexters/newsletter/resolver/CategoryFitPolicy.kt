package com.nexters.newsletter.resolver

import com.nexters.external.service.category.CategoryFitThresholds
import com.nexters.external.service.category.ContentCategoryScoreSnapshot

internal object CategoryFitPolicy {
    private const val DOMINANT_CATEGORY_RATIO = 1.5
    private const val DOMINANT_CATEGORY_GAP = 8.0

    fun hasCategoryFit(
        categoryScores: List<ContentCategoryScoreSnapshot>,
        requestedCategoryIds: Set<Long>,
    ): Boolean {
        val positiveCategoryScores = categoryScores.filter { it.totalScore > 0.0 }

        if (positiveCategoryScores.isEmpty()) {
            return true
        }

        val providerCategoryIds =
            positiveCategoryScores
                .filter { it.providerScore > 0.0 }
                .map { it.categoryId }
                .toSet()

        if (providerCategoryIds.isNotEmpty() && providerCategoryIds.none { it in requestedCategoryIds }) {
            return false
        }

        val requestedScore =
            positiveCategoryScores
                .filter { it.categoryId in requestedCategoryIds }
                .sumOf { it.totalScore }
        val competingScore =
            positiveCategoryScores
                .filter { it.categoryId !in requestedCategoryIds }
                .maxOfOrNull { it.totalScore } ?: 0.0

        if (requestedScore <= 0.0) {
            return false
        }

        if (requestedCategoryIds.size == 1) {
            return competingScore <= 0.0 ||
                requestedScore >= competingScore + CategoryFitThresholds.SINGLE_CATEGORY_MIN_SCORE_MARGIN
        }

        return competingScore < requestedScore * DOMINANT_CATEGORY_RATIO ||
            competingScore - requestedScore < DOMINANT_CATEGORY_GAP
    }
}
