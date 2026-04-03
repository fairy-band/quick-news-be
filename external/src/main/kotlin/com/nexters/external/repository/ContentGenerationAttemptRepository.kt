package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentGenerationAttempt
import org.springframework.data.jpa.repository.JpaRepository

interface ContentGenerationAttemptRepository : JpaRepository<ContentGenerationAttempt, Long> {
    fun findByContentOrderByCreatedAtDesc(content: Content): List<ContentGenerationAttempt>
}
