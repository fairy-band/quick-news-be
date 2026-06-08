package com.nexters.newsletter.resolver

import com.nexters.external.entity.ContentProvider
import com.nexters.external.service.CategoryService
import com.nexters.external.service.ExposureContentService
import org.springframework.stereotype.Component

@Component
class CategoryProviderCandidateSource(
    private val categoryService: CategoryService,
    private val exposureContentService: ExposureContentService,
) : CandidateSource {
    override val name: String = "category_provider"
    override val order: Int = 20
    override val defaultLimit: Int = 60

    override fun fetch(request: CandidateSourceRequest): List<CandidateSeed> {
        val contentProviderIds =
            categoryService
                .getContentProvidersByCategoryIds(request.categoryIds)
                .mapNotNull { (it as? ContentProvider)?.id }

        if (contentProviderIds.isEmpty()) {
            return emptyList()
        }

        return exposureContentService
            .getNotExposedRecommendationCandidatesByContentProviderIds(
                userId = request.userId,
                contentProviderIds = contentProviderIds,
                publishedFrom = request.publishedFrom,
                limit = request.limit,
            ).map { candidate ->
                CandidateSeed(
                    candidate = candidate,
                    signals =
                        listOf(
                            CandidateSourceSignal(
                                source = name,
                                score = PROVIDER_SIGNAL_SCORE,
                                confidence = PROVIDER_SIGNAL_CONFIDENCE,
                                reason = "matched category providers in ${request.window}",
                            ),
                        ),
                )
            }
    }

    companion object {
        private const val PROVIDER_SIGNAL_SCORE = 0.7
        private const val PROVIDER_SIGNAL_CONFIDENCE = 0.55
    }
}
