package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ContentProviderService
import org.springframework.stereotype.Component

@Component
class CandidateScoringSourceFactory(
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val contentProviderService: ContentProviderService,
    private val categoryService: CategoryService,
) {
    fun createContext(
        candidates: List<ExposureContentRecommendationCandidateRow>,
        candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        categoryIds: List<Long>,
    ): CandidateScoringSourceContext {
        if (candidates.isEmpty()) {
            return CandidateScoringSourceContext(
                candidates = emptyList(),
                candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
                keywordWeightsByKeywordId = keywordWeightsByKeyword.toKeywordIdMap(),
                keywordsByContentId = emptyMap(),
                categoryIds = categoryIds,
                categoryMatchWeights = emptyMap(),
                keywordCategoryWeightsByKeywordId = emptyMap(),
                contentProviderCategoryWeightsByProviderId = emptyMap(),
            )
        }

        val contentIds = candidates.map { it.contentId }
        val keywordsByContentId =
            contentKeywordMappingRepository
                .findKeywordsByContentIds(contentIds)
                .groupBy({ it.contentId }, { it.keyword })
        val contentProviderIds = candidates.mapNotNull { it.contentProviderId }.distinct()
        val allCategoryMatchWeights = contentProviderService.getAllCategoryMatchWeights(contentProviderIds)
        val categoryIdSet = categoryIds.toSet()
        val categoryMatchWeights =
            allCategoryMatchWeights.filterKeys { (_, categoryId) ->
                categoryId in categoryIdSet
            }
        val keywordIds =
            keywordsByContentId
                .values
                .flatten()
                .mapNotNull { it.id }
                .distinct()

        return CandidateScoringSourceContext(
            candidates = candidates,
            candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
            keywordWeightsByKeywordId = keywordWeightsByKeyword.toKeywordIdMap(),
            keywordsByContentId = keywordsByContentId,
            categoryIds = categoryIds,
            categoryMatchWeights = categoryMatchWeights,
            keywordCategoryWeightsByKeywordId =
                categoryService
                    .getKeywordCategoryWeightsByKeywordIds(keywordIds)
                    .toCategoryWeightsByFirstId(),
            contentProviderCategoryWeightsByProviderId = allCategoryMatchWeights.toCategoryWeightsByFirstId(),
        )
    }

    fun createSources(
        context: CandidateScoringSourceContext,
        multiplier: Double,
    ): Map<ExposureContentRecommendationCandidateRow, RecommendCalculateSource> =
        context.candidates.associateWith { candidate ->
            val contentKeywords = context.keywordsByContentId[candidate.contentId] ?: emptyList()
            RecommendCalculateSource(
                title = candidate.title,
                provocativeHeadline = candidate.provocativeHeadline,
                summaryContent = candidate.summaryContent,
                newsletterName = candidate.newsletterName,
                contentProviderName = candidate.contentProviderName,
                keywordNames = contentKeywords.map { it.name },
                candidateSignals = context.candidateSignalsByExposureContentId[candidate.exposureContentId] ?: emptyList(),
                positiveKeywordSources = extractPositiveKeywordSources(contentKeywords, context.keywordWeightsByKeywordId, multiplier),
                negativeKeywordSources = extractNegativeKeywordSources(contentKeywords, context.keywordWeightsByKeywordId),
                publishedDate = candidate.publishedAt,
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = calculateCategoryMatchBonus(candidate.contentProviderId, context),
            )
        }

    private fun extractPositiveKeywordSources(
        keywords: List<ReservedKeyword>,
        keywordWeightsByKeywordId: Map<Long, Double>,
        multiplier: Double,
    ): List<PositiveKeywordSource> =
        keywords
            .filter { keyword -> (keyword.id?.let { keywordWeightsByKeywordId[it] } ?: 0.0) > 0 }
            .map { keyword ->
                PositiveKeywordSource(
                    weight = (keyword.id?.let { keywordWeightsByKeywordId[it] } ?: 0.0) * multiplier,
                    keywordName = keyword.name,
                )
            }

    private fun extractNegativeKeywordSources(
        keywords: List<ReservedKeyword>,
        keywordWeightsByKeywordId: Map<Long, Double>,
    ): List<NegativeKeywordSource> =
        keywords
            .filter { keyword -> (keyword.id?.let { keywordWeightsByKeywordId[it] } ?: 0.0) < 0 }
            .map { keyword ->
                NegativeKeywordSource(
                    weight = keyword.id?.let { keywordWeightsByKeywordId[it] } ?: 0.0,
                    keywordName = keyword.name,
                )
            }

    private fun calculateCategoryMatchBonus(
        contentProviderId: Long?,
        context: CandidateScoringSourceContext,
    ): Double {
        contentProviderId ?: return 0.0

        return context.categoryIds.sumOf { categoryId ->
            context.categoryMatchWeights[contentProviderId to categoryId] ?: 0.0
        }
    }

    private fun Map<ReservedKeyword, Double>.toKeywordIdMap(): Map<Long, Double> =
        mapNotNull { (keyword, weight) ->
            keyword.id?.let { it to weight }
        }.toMap()

    private fun Map<Pair<Long, Long>, Double>.toCategoryWeightsByFirstId(): Map<Long, List<CategoryWeight>> =
        entries.groupBy(
            keySelector = { (key, _) -> key.first },
            valueTransform = { (key, weight) -> CategoryWeight(categoryId = key.second, weight = weight) },
        )
}

data class CandidateScoringSourceContext(
    val candidates: List<ExposureContentRecommendationCandidateRow>,
    val candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
    val keywordWeightsByKeywordId: Map<Long, Double>,
    val keywordsByContentId: Map<Long, List<ReservedKeyword>>,
    val categoryIds: List<Long>,
    val categoryMatchWeights: Map<Pair<Long, Long>, Double>,
    val keywordCategoryWeightsByKeywordId: Map<Long, List<CategoryWeight>>,
    val contentProviderCategoryWeightsByProviderId: Map<Long, List<CategoryWeight>>,
)

data class CategoryWeight(
    val categoryId: Long,
    val weight: Double,
)
