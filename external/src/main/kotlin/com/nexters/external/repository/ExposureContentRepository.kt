package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExposureContentRepository : JpaRepository<ExposureContent, Long> {
    fun findByContent(content: Content): ExposureContent?
}
