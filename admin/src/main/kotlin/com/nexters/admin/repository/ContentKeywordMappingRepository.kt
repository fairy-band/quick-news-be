package com.nexters.admin.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContentKeywordMappingRepository : JpaRepository<ContentKeywordMapping, Long> {
    fun findByContent(content: Content): List<ContentKeywordMapping>

    fun findByContentAndKeyword(
        content: Content,
        keyword: ReservedKeyword
    ): ContentKeywordMapping?
}
