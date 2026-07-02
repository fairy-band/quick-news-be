package com.nexters.external.repository

import com.nexters.external.entity.ExposureContentMarkdown
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExposureContentMarkdownRepository : JpaRepository<ExposureContentMarkdown, Long> {
    fun findByExposureContentId(exposureContentId: Long): ExposureContentMarkdown?
}
