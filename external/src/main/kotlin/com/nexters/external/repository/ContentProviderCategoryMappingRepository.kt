package com.nexters.external.repository

import com.nexters.external.entity.ContentProviderCategoryMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderCategoryMappingRepository : JpaRepository<ContentProviderCategoryMapping, Long> {
    @Query(
        """
        SELECT cpcm
        FROM ContentProviderCategoryMapping cpcm
        WHERE cpcm.contentProvider.id IN (:contentProviderIds)
          AND cpcm.category.id IN (:categoryIds)
        """,
    )
    fun findByContentProviderIdInAndCategoryIdIn(
        contentProviderIds: List<Long>,
        categoryIds: List<Long>,
    ): List<ContentProviderCategoryMapping>
}
