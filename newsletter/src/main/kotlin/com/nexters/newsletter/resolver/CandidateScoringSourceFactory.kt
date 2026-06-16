package com.nexters.newsletter.resolver

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.ContentProviderService
import com.nexters.external.service.category.ContentCategoryScoreService
import com.nexters.external.service.category.ContentCategoryScoreSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val candidateScoringSourceFactoryLogger = LoggerFactory.getLogger(CandidateScoringSourceFactory::class.java)
private const val SLOW_CONTEXT_LOG_THRESHOLD_MS = 500L

@Component
class CandidateScoringSourceFactory(
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val contentProviderService: ContentProviderService,
    private val contentCategoryScoreService: ContentCategoryScoreService,
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
                categoryScoresByContentId = emptyMap(),
            )
        }

        val trace = CandidateScoringContextTrace()
        val contentIds = candidates.map { it.contentId }.distinct()
        val categoryScoresByContentId =
            trace.measure("loadCategoryScores") {
                contentCategoryScoreService.getScoresByContentIds(contentIds)
            }

        trace.logIfSlow(
            candidateCount = candidates.size,
            contentCount = contentIds.size,
            categoryCount = categoryIds.size,
        )

        return CandidateScoringSourceContext(
            candidates = candidates,
            candidateSignalsByExposureContentId = candidateSignalsByExposureContentId,
            keywordWeightsByKeywordId = keywordWeightsByKeyword.toKeywordIdMap(),
            keywordsByContentId = emptyMap(),
            categoryIds = categoryIds,
            categoryMatchWeights = emptyMap(),
            categoryScoresByContentId = categoryScoresByContentId,
        )
    }

    fun loadScoringFeatures(context: CandidateScoringSourceContext): CandidateScoringSourceContext {
        if (context.candidates.isEmpty()) {
            return context
        }

        val contentIds = context.candidates.map { it.contentId }.distinct()
        val keywordsByContentId =
            contentKeywordMappingRepository
                .findKeywordFeaturesByContentIds(contentIds)
                .groupBy(
                    keySelector = { it.contentId },
                    valueTransform = { CandidateKeywordFeature(id = it.keywordId, name = it.keywordName) },
                )
        val contentProviderIds = context.candidates.mapNotNull { it.contentProviderId }.distinct()
        val categoryMatchWeights = contentProviderService.getCategoryMatchWeights(contentProviderIds, context.categoryIds)

        return context.copy(
            keywordsByContentId = keywordsByContentId,
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
                positiveKeywordSources = extractPositiveKeywordSources(contentKeywords, context.keywordWeightsByKeywordId, multiplier),
                negativeKeywordSources = extractNegativeKeywordSources(contentKeywords, context.keywordWeightsByKeywordId),
                publishedDate = candidate.publishedAt,
                publisherDuplicateCandidateCount = 0,
                categoryMatchBonus = calculateCategoryMatchBonus(candidate.contentProviderId, context),
            )
        }

    private fun extractPositiveKeywordSources(
        keywords: List<CandidateKeywordFeature>,
        keywordWeightsByKeywordId: Map<Long, Double>,
        multiplier: Double,
    ): List<PositiveKeywordSource> =
        keywords
            .filter { keyword -> (keywordWeightsByKeywordId[keyword.id] ?: 0.0) > 0 }
            .map { keyword ->
                PositiveKeywordSource(
                    weight = (keywordWeightsByKeywordId[keyword.id] ?: 0.0) * multiplier,
                    keywordName = keyword.name,
                )
            }

    private fun extractNegativeKeywordSources(
        keywords: List<CandidateKeywordFeature>,
        keywordWeightsByKeywordId: Map<Long, Double>,
    ): List<NegativeKeywordSource> =
        keywords
            .filter { keyword -> (keywordWeightsByKeywordId[keyword.id] ?: 0.0) < 0 }
            .map { keyword ->
                NegativeKeywordSource(
                    weight = keywordWeightsByKeywordId[keyword.id] ?: 0.0,
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
}

data class CandidateKeywordFeature(
    val id: Long,
    val name: String,
)

data class CandidateScoringSourceContext(
    val candidates: List<ExposureContentRecommendationCandidateRow>,
    val candidateSignalsByExposureContentId: Map<Long, List<CandidateSourceSignal>>,
    val keywordWeightsByKeywordId: Map<Long, Double>,
    val keywordsByContentId: Map<Long, List<CandidateKeywordFeature>>,
    val categoryIds: List<Long>,
    val categoryMatchWeights: Map<Pair<Long, Long>, Double>,
    val categoryScoresByContentId: Map<Long, List<ContentCategoryScoreSnapshot>>,
)

private class CandidateScoringContextTrace {
    val timings = linkedMapOf<String, Long>()

    fun <T> measure(
        operation: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            timings[operation] = (System.nanoTime() - startedAt) / 1_000_000
        }
    }

    fun logIfSlow(
        candidateCount: Int,
        contentCount: Int,
        categoryCount: Int,
    ) {
        val totalMillis = timings.values.sum()
        if (totalMillis < SLOW_CONTEXT_LOG_THRESHOLD_MS) {
            return
        }

        candidateScoringSourceFactoryLogger.info(
            "candidate_scoring_context_loaded candidateCount={} contentCount={} categoryCount={} timingsMs={}",
            candidateCount,
            contentCount,
            categoryCount,
            timings,
        )
    }
}
