package com.nexters.external.repository

import com.nexters.external.entity.GeminiRateLimit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface GeminiRateLimitRepository : JpaRepository<GeminiRateLimit, Long> {
    fun findByModelNameAndLimitDate(
        modelName: String,
        limitDate: LocalDate
    ): GeminiRateLimit?
}
