package com.nexters.external.repository

import com.nexters.external.entity.ContentProviderCategoryMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContentProviderCategoryMappingRepository : JpaRepository<ContentProviderCategoryMapping, Long> {
    @Query(
        """
        SELECT cpcm
        FROM ContentProviderCategoryMapping cpcm
        JOIN FETCH cpcm.contentProvider cp
        JOIN FETCH cpcm.category c
        WHERE cp.id IN (:contentProviderIds)
        AND c.id IN (:categoryIds)
    """
    )
    fun findByContentProviderIdInAndCategoryIdIn(
        @Param("contentProviderIds") contentProviderIds: List<Long>,
        @Param("categoryIds") categoryIds: List<Long>,
    ): List<ContentProviderCategoryMapping>
}
