package com.nexters.admin.repository

import com.nexters.external.entity.Content
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ContentRepository : JpaRepository<Content, Long> {
    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
    """
    )
    fun findContentsByCategory(
        @Param("categoryId") categoryId: Long
    ): List<Content>

    fun findByNewsletterName(newsletterName: String): List<Content>
}
