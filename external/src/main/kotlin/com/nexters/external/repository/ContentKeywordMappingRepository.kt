package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}
