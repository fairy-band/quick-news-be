package com.nexters.external.repository

import com.nexters.external.entity.ContentCategoryScore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContentCategoryScoreRepository : JpaRepository<ContentCategoryScore, Long> {
    @Modifying
    @Query(
        """
        DELETE FROM ContentCategoryScore score
        WHERE score.contentId = :contentId
        """,
    )
    fun deleteByContentId(
        @Param("contentId") contentId: Long,
    ): Int
}
