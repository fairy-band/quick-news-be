package com.nexters.external.repository

import com.nexters.external.entity.ContentProcessingState
import com.nexters.external.enums.ContentProcessingStage
import org.springframework.data.jpa.repository.JpaRepository

interface ContentProcessingStateRepository : JpaRepository<ContentProcessingState, Long> {
    fun findByContentIdInAndStage(contentIds: Collection<Long>, stage: ContentProcessingStage): List<ContentProcessingState>
    fun findByContentIdAndStage(contentId: Long, stage: ContentProcessingStage): ContentProcessingState?
}
