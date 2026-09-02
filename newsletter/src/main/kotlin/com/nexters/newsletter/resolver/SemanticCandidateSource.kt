package com.nexters.newsletter.resolver

import com.nexters.external.service.ExposureContentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(SemanticCandidateSource::class.java)

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

        return try {
            exposureContentService
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
        } catch (e: Exception) {
            logger.warn("시맨틱 벡터 후보군 조회 중 예외 발생 (기존 규칙 후보군으로 안전 폴백). userId: {}, error: {}", request.userId, e.message)
            emptyList()
        }
    }

    companion object {
        private const val SEMANTIC_VECTOR_SIGNAL_SCORE = 1.2
        private const val SEMANTIC_VECTOR_SIGNAL_CONFIDENCE = 0.90
    }
}

