package com.nexters.newsletter.resolver

import com.nexters.external.service.CategoryService
import com.nexters.external.service.ExposureContentService
import org.springframework.stereotype.Component

@Component
class CategoryKeywordCandidateSource(
    private val categoryService: CategoryService,
    private val exposureContentService: ExposureContentService,
) : CandidateSource {
    override val name: String = "category_keyword"
    override val order: Int = 10
    override val defaultLimit: Int = 100

    override fun fetch(request: CandidateSourceRequest): List<CandidateSeed> {
        val reservedKeywordIds =
            categoryService
                .getKeywordsByCategoryIds(request.categoryIds)
                .mapNotNull { it.id }

        if (reservedKeywordIds.isEmpty()) {
            return emptyList()
        }

        return exposureContentService
            .getNotExposedRecommendationCandidatesByReservedKeywordIds(
                userId = request.userId,
                reservedKeywordIds = reservedKeywordIds,
                publishedFrom = request.publishedFrom,
                limit = request.limit,
            ).map { candidate ->
                CandidateSeed(
                    candidate = candidate,
                    signals =
                        listOf(
                            CandidateSourceSignal(
                                source = name,
                                score = KEYWORD_SIGNAL_SCORE,
                                confidence = KEYWORD_SIGNAL_CONFIDENCE,
                                reason = "matched category keywords in ${request.window}",
                            ),
                        ),
                )
            }
    }

    companion object {
        private const val KEYWORD_SIGNAL_SCORE = 1.0
        private const val KEYWORD_SIGNAL_CONFIDENCE = 0.75
    }
}
