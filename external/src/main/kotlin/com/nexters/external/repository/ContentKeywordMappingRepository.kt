package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContentKeywordMappingRepository : JpaRepository<ContentKeywordMapping, Long> {
    fun findByContent(content: Content): List<ContentKeywordMapping>

    fun findByContent(
        content: Content,
        pageable: Pageable
    ): Page<ContentKeywordMapping>

    fun findByContentAndKeyword(
        content: Content,
        keyword: ReservedKeyword
    ): ContentKeywordMapping?

    @Query(
        """
        SELECT ckm.content.id as contentId, kw.id as keywordId, kw.name as keywordName
        FROM ContentKeywordMapping ckm
        JOIN ckm.keyword kw
        WHERE ckm.content.id IN :contentIds
        """
    )
    fun findKeywordFeaturesByContentIds(
        @Param("contentIds") contentIds: List<Long>
    ): List<ContentKeywordFeatureProjection>
}

interface ContentKeywordFeatureProjection {
    val contentId: Long
    val keywordId: Long
    val keywordName: String
}
