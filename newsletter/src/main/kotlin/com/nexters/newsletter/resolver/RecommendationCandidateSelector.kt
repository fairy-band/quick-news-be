package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RecommendationCandidateSelector(
    private val scoringSourceFactory: CandidateScoringSourceFactory,
    private val ranker: RecommendationCandidateRanker,
    private val publisherDiversityPolicy: PublisherDiversityPolicy,
) {
    fun select(request: RecommendationCandidateSelectionRequest): List<ExposureContentRecommendationCandidateRow> {
        if (request.candidates.isEmpty() || request.limit <= 0) {
            return emptyList()
        }

        val context =
            scoringSourceFactory.createContext(
                candidates = request.candidates,
                candidateSignalsByExposureContentId = request.candidateSignalsByExposureContentId,
                keywordWeightsByKeyword = request.keywordWeightsByKeyword,
                categoryIds = request.categoryIds,
            )
        val filteredContext = context.filterByCategoryFit()
        if (filteredContext.candidates.isEmpty()) {
            return emptyList()
        }
        if (filteredContext.candidates.size < context.candidates.size) {
            logger.debug(
                "카테고리 적합도 필터 적용. categoryIds: {}, before: {}, after: {}",
                request.categoryIds,
                context.candidates.size,
                filteredContext.candidates.size,
            )
        }

        val sourcesByCandidate = scoringSourceFactory.createSources(filteredContext, multiplier = 1.0)
        val scoredCandidates = ranker.rank(sourcesByCandidate)
        val positiveScoreCandidates =
            scoredCandidates
                .filter { it.recommendScore > 0 }
                .map { it.candidate }

        if (positiveScoreCandidates.size >= request.limit) {
            return publisherDiversityPolicy.apply(
                candidates = positiveScoreCandidates,
                sourcesByCandidate = sourcesByCandidate,
                limit = request.limit,
            )
        }

        val selectedCandidates = LinkedHashSet<ExposureContentRecommendationCandidateRow>(positiveScoreCandidates)

        for (multiplier in FALLBACK_MULTIPLIERS) {
            if (selectedCandidates.size >= request.limit) {
                break
            }

            val amplifiedSourcesByCandidate = scoringSourceFactory.createSources(filteredContext, multiplier)
            val amplifiedCandidates =
                ranker
                    .rank(amplifiedSourcesByCandidate)
                    .filter { it.candidate !in selectedCandidates }
                    .filter { it.recommendScore > 0 }
                    .map { it.candidate }
            val additionalNeeded = request.limit - selectedCandidates.size
            selectedCandidates.addAll(amplifiedCandidates.take(additionalNeeded))
        }

        return selectedCandidates.take(request.limit)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RecommendationCandidateSelector::class.java)
        private val FALLBACK_MULTIPLIERS = listOf(2.0, 3.0, 4.0)
        private const val DOMINANT_CATEGORY_RATIO = 1.5
        private const val DOMINANT_CATEGORY_GAP = 8.0
        private const val SINGLE_CATEGORY_MIN_SCORE_MARGIN = 2.0
        private const val PROVIDER_CATEGORY_FIT_WEIGHT_CAP = 4.0
    }

    private fun CandidateScoringSourceContext.filterByCategoryFit(): CandidateScoringSourceContext {
        val requestedCategoryIds = categoryIds.toSet()
        if (requestedCategoryIds.isEmpty() || candidates.isEmpty()) {
            return this
        }

        return copy(
            candidates =
                candidates.filter { candidate ->
                    hasCategoryFit(candidate, requestedCategoryIds)
                },
        )
    }

    private fun CandidateScoringSourceContext.hasCategoryFit(
        candidate: ExposureContentRecommendationCandidateRow,
        requestedCategoryIds: Set<Long>,
    ): Boolean {
        val categoryScores = mutableMapOf<Long, CategoryFitScore>()

        keywordsByContentId[candidate.contentId]
            .orEmpty()
            .mapNotNull { it.id }
            .forEach { keywordId ->
                keywordCategoryWeightsByKeywordId[keywordId]
                    .orEmpty()
                    .filter { it.weight > 0.0 }
                    .forEach { categoryWeight ->
                        categoryScores.addKeywordScore(categoryWeight.categoryId, categoryWeight.weight)
                    }
            }

        val providerCategoryWeights =
            candidate.contentProviderId
                ?.let { contentProviderCategoryWeightsByProviderId[it] }
                .orEmpty()
                .filter { it.weight > 0.0 }

        if (providerCategoryWeights.isNotEmpty() && providerCategoryWeights.none { it.categoryId in requestedCategoryIds }) {
            return false
        }

        providerCategoryWeights.forEach { categoryWeight ->
            categoryScores.addProviderScore(categoryWeight.categoryId, categoryWeight.weight)
        }

        if (categoryScores.isEmpty()) {
            return true
        }

        val requestedScore =
            categoryScores
                .filterKeys { it in requestedCategoryIds }
                .values
                .sumOf { it.total }
        val strongestOtherScore =
            categoryScores
                .filterKeys { it !in requestedCategoryIds }
                .values
                .maxOfOrNull { it.total } ?: 0.0

        if (requestedScore <= 0.0) {
            return false
        }

        if (requestedCategoryIds.size == 1) {
            return strongestOtherScore <= 0.0 ||
                requestedScore >= strongestOtherScore + SINGLE_CATEGORY_MIN_SCORE_MARGIN
        }

        return strongestOtherScore < requestedScore * DOMINANT_CATEGORY_RATIO ||
            strongestOtherScore - requestedScore < DOMINANT_CATEGORY_GAP
    }

    private fun MutableMap<Long, CategoryFitScore>.addKeywordScore(
        categoryId: Long,
        weight: Double,
    ) {
        val current = this[categoryId] ?: CategoryFitScore()
        this[categoryId] = current.copy(keywordScore = current.keywordScore + weight)
    }

    private fun MutableMap<Long, CategoryFitScore>.addProviderScore(
        categoryId: Long,
        weight: Double,
    ) {
        val current = this[categoryId] ?: CategoryFitScore()
        this[categoryId] =
            current.copy(
                providerScore = current.providerScore + weight.coerceAtMost(PROVIDER_CATEGORY_FIT_WEIGHT_CAP),
            )
    }
}

private data class CategoryFitScore(
    val keywordScore: Double = 0.0,
    val providerScore: Double = 0.0,
) {
    val total: Double = keywordScore + providerScore
}

data class RecommendationCandidateSelectionRequest(
    val candidates: List<ExposureContentRecommendationCandidateRow>,
    val candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
    val keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    val categoryIds: List<Long>,
    val limit: Int,
)
