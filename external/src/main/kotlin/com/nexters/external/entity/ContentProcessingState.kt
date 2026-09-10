package com.nexters.external.entity

import com.nexters.external.enums.ContentProcessingStage
import com.nexters.external.enums.ContentProcessingStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "content_processing_states",
    indexes = [
        Index(name = "idx_content_processing_states_stage_status_retry", columnList = "stage,status,retry_at"),
        Index(name = "idx_content_processing_states_content_stage", columnList = "content_id,stage", unique = true),
    ],
)
class ContentProcessingState(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @Column(name = "content_id", nullable = false) val contentId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val stage: ContentProcessingStage,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: ContentProcessingStatus = ContentProcessingStatus.PENDING,
    @Column(name = "attempt_count", nullable = false) var attemptCount: Int = 0,
    @Column(name = "retry_at") var retryAt: LocalDateTime? = null,
    @Column(name = "last_error", columnDefinition = "TEXT") var lastError: String? = null,
    @Column(name = "updated_at", nullable = false) var updatedAt: LocalDateTime = LocalDateTime.now(),
)
