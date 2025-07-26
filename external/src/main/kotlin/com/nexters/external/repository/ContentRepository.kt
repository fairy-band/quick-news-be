package com.nexters.external.repository

import com.nexters.external.entity.Content
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ContentRepository : JpaRepository<Content, Long> {
    @Query(
        """
        SELECT c
        FROM Content c
        JOIN ContentKeywordMapping ckm ON c = ckm.content
        WHERE ckm.keyword.id IN :reservedKeywordIds
    """
    )
    fun findByReservedKeywordIds(reservedKeywordIds: List<Long>): List<Content>
}
