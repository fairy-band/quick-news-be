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

    /**
     * Content ID별로 키워드 목록을 Map으로 반환
     */
    @Query(
        """
        SELECT ckm.content.id as contentId, ckm.keyword as keyword
        FROM ContentKeywordMapping ckm
        WHERE ckm.content.id IN :contentIds
        """
    )
    fun findKeywordsByContentIds(
        @Param("contentIds") contentIds: List<Long>
    ): List<ContentKeywordProjection>
}

interface ContentKeywordProjection {
    val contentId: Long
    val keyword: ReservedKeyword
}
