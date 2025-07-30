package com.nexters.external.repository

import com.nexters.external.entity.Content
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

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

    @Query(
        """
        SELECT c
        FROM Content c
        JOIN ContentKeywordMapping ckm ON c = ckm.content
        WHERE ckm.keyword.id IN :reservedKeywordIds
    """
    )
    fun findByReservedKeywordIds(
        reservedKeywordIds: List<Long>,
        pageable: Pageable
    ): Page<Content>

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

    @Query(
        """
        SELECT DISTINCT c FROM Content c
        JOIN ContentKeywordMapping ckm ON c.id = ckm.content.id
        JOIN CategoryKeywordMapping catkm ON ckm.keyword.id = catkm.keyword.id
        WHERE catkm.category.id = :categoryId
    """
    )
    fun findContentsByCategory(
        @Param("categoryId") categoryId: Long,
        pageable: Pageable
    ): Page<Content>

    @Query("SELECT DISTINCT c.newsletterName FROM Content c ORDER BY c.newsletterName")
    fun findDistinctNewsletterNames(): List<String>
}
