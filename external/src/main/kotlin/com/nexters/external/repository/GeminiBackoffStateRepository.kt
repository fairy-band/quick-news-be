package com.nexters.external.repository

import com.nexters.external.entity.GeminiBackoffState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface GeminiBackoffStateRepository : JpaRepository<GeminiBackoffState, Long> {
    fun findByModelNameAndLimitType(modelName: String, limitType: String): GeminiBackoffState?

    fun findByModelNameAndBlockedUntilAfterOrderByBlockedUntilDesc(
        modelName: String,
        blockedUntil: LocalDateTime,
    ): List<GeminiBackoffState>

    fun findByModelName(modelName: String): List<GeminiBackoffState>
}
