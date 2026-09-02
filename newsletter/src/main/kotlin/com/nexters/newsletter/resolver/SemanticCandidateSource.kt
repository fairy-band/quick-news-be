package com.nexters.newsletter.resolver

import com.nexters.external.service.ExposureContentService
import org.springframework.stereotype.Component

@Component
class SemanticCandidateSource(
    private val exposureContentService: ExposureContentService,
) : CandidateSource {
    override val name: String = "semantic_vector"
    override val order: Int = 4
    override val defaultLimit: Int = 60

    override fun fetch(request: CandidateSourceRequest): List<CandidateSeed> {
        if (request.context.categoryIds.isEmpty()) {
            return emptyList()
        }

        return exposureContentService
            .getNotExposedSemanticRecommendationCandidates(
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
                                score = SEMANTIC_VECTOR_SIGNAL_SCORE,
                                confidence = SEMANTIC_VECTOR_SIGNAL_CONFIDENCE,
                                reason = "matched bge-m3 pgvector semantic cosine embedding in ${request.window}",
                            ),
                        ),
                )
            }
    }

    companion object {
        private const val SEMANTIC_VECTOR_SIGNAL_SCORE = 1.2
        private const val SEMANTIC_VECTOR_SIGNAL_CONFIDENCE = 0.90
    }
}
