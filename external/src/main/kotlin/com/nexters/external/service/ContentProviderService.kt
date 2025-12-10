package com.nexters.external.service

import com.nexters.external.entity.ContentProvider
import com.nexters.external.repository.ContentProviderCategoryMappingRepository
import com.nexters.external.repository.ContentProviderRepository
import org.springframework.stereotype.Service

@Service
class ContentProviderService(
    private val contentProviderRepository: ContentProviderRepository,
    private val contentProviderCategoryMappingRepository: ContentProviderCategoryMappingRepository,
) {
    fun getByName(name: String): ContentProvider? = contentProviderRepository.findByName(name)

    /**
     * ContentProvider와 Category 매핑의 가중치를 조회합니다.
     * @author char-yb
     * @param contentProviderIds ContentProvider ID 목록
     * @param categoryIds Category ID 목록
     * @return Map<Pair<ContentProviderId, CategoryId>, Weight>
     *
     * 예시:
     * - ContentProvider(id=1) ↔ Category(id=10, weight=150.0)
     * - ContentProvider(id=2) ↔ Category(id=10, weight=100.0)
     * → Map: {(1, 10) to 150.0, (2, 10) to 100.0}
     */
    fun getCategoryMatchWeights(
        contentProviderIds: List<Long>,
        categoryIds: List<Long>,
    ): Map<Pair<Long, Long>, Double> {
        if (contentProviderIds.isEmpty() || categoryIds.isEmpty()) {
            return emptyMap()
        }

        return contentProviderCategoryMappingRepository
            .findByContentProviderIdInAndCategoryIdIn(contentProviderIds, categoryIds)
            .associate { mapping ->
                val key = mapping.contentProvider.id!! to mapping.category.id!!
                key to mapping.weight
            }
    }
}
