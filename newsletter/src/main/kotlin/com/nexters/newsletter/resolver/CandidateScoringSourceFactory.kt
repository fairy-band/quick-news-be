package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.ContentProviderService
import org.springframework.stereotype.Component

@Component
class CandidateScoringSourceFactory(
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val contentProviderService: ContentProviderService,
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
                keywordWeightsByKeyword = keywordWeightsByKeyword,
                keywordsByContentId = emptyMap(),
                categoryIds = categoryIds,
                categoryMatchWeights = emptyMap(),
            )
        }

        val contentIds = candidates.map { it.contentId }
        val keywordsByContentId =
            contentKeywordMappingRepository
                .findKeywordsByContentIds(contentIds)
                .groupBy({ it.contentId }, { it.keyword })
        val contentProviderIds = candidates.mapNotNull { it.contentProviderId }.distinct()
        val categoryMatchWeights = contentProviderService.getCategoryMatchWeights(contentProviderIds, categoryIds)

        return CandidateScoringSourceContext(
            candidates = candidates,
            candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
            keywordWeightsByKeyword = keywordWeightsByKeyword,
            keywordsByContentId = keywordsByContentId,
            categoryIds = categoryIds,
            categoryMatchWeights = categoryMatchWeights,
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
                positiveKeywordSources = extractPositiveKeywordSources(contentKeywords, context.keywordWeightsByKeyword, multiplier),
                negativeKeywordSources = extractNegativeKeywordSources(contentKeywords, context.keywordWeightsByKeyword),
                publishedDate = candidate.publishedAt,
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = calculateCategoryMatchBonus(candidate.contentProviderId, context),
            )
        }

    private fun extractPositiveKeywordSources(
        keywords: List<ReservedKeyword>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
        multiplier: Double,
    ): List<PositiveKeywordSource> =
        keywords
            .filter { keyword -> (keywordWeightsByKeyword[keyword] ?: 0.0) > 0 }
            .map { keyword ->
                PositiveKeywordSource(
                    weight = (keywordWeightsByKeyword[keyword] ?: 0.0) * multiplier,
                    keywordName = keyword.name,
                )
            }

    private fun extractNegativeKeywordSources(
        keywords: List<ReservedKeyword>,
        keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    ): List<NegativeKeywordSource> =
        keywords
            .filter { keyword -> (keywordWeightsByKeyword[keyword] ?: 0.0) < 0 }
            .map { keyword ->
                NegativeKeywordSource(
                    weight = keywordWeightsByKeyword[keyword] ?: 0.0,
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
}

data class CandidateScoringSourceContext(
    val candidates: List<ExposureContentRecommendationCandidateRow>,
    val candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
    val keywordWeightsByKeyword: Map<ReservedKeyword, Double>,
    val keywordsByContentId: Map<Long, List<ReservedKeyword>>,
    val categoryIds: List<Long>,
    val categoryMatchWeights: Map<Pair<Long, Long>, Double>,
)
