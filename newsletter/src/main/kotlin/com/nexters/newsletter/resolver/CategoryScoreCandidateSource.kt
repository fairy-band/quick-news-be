package com.nexters.newsletter.resolver

import com.nexters.external.service.ExposureContentService
import org.springframework.stereotype.Component

@Component
class CategoryScoreCandidateSource(
    private val exposureContentService: ExposureContentService,
) : CandidateSource {
    override val name: String = "category_score"
    override val order: Int = 5
    override val defaultLimit: Int = 140

    override fun fetch(request: CandidateSourceRequest): List<CandidateSeed> {
        if (request.context.categoryIds.isEmpty()) {
            return emptyList()
        }

        return exposureContentService
            .getNotExposedRecommendationCandidatesByCategoryIds(
                userId = request.userId,
                categoryIds = request.context.categoryIds,
                publishedFrom = request.publishedFrom,
                limit = request.limit,
            ).map { candidate ->
                CandidateSeed(
                    candidate = candidate,
                    signals =
                        listOf(
                            CandidateSourceSignal(
                                source = name,
                                score = CATEGORY_SCORE_SIGNAL_SCORE,
                                confidence = CATEGORY_SCORE_SIGNAL_CONFIDENCE,
                                reason = "matched precomputed category scores in ${request.window}",
                            ),
                        ),
                )
            }
    }

    companion object {
        private const val CATEGORY_SCORE_SIGNAL_SCORE = 1.0
        private const val CATEGORY_SCORE_SIGNAL_CONFIDENCE = 0.85
    }
}
