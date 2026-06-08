package com.nexters.external.repository

import com.nexters.external.entity.ContentKeywordMatchScore
import com.nexters.external.enums.KeywordMatchSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContentKeywordMatchScoreRepository : JpaRepository<ContentKeywordMatchScore, Long> {
    fun findByContentIdAndKeywordIdAndSource(
        contentId: Long,
        keywordId: Long,
        source: KeywordMatchSource,
    ): ContentKeywordMatchScore?
}
