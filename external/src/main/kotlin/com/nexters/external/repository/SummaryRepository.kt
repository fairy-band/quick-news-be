package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.Summary
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SummaryRepository : JpaRepository<Summary, Long> {
    fun findByContent(content: Content): List<Summary>

    fun findByContent(
        content: Content,
        pageable: Pageable
    ): Page<Summary>
}
