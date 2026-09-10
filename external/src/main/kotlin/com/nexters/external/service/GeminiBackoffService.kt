package com.nexters.external.service

import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.GeminiBackoffState
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.GeminiBackoffStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

@Service
class GeminiBackoffService(
    private val backoffStateRepository: GeminiBackoffStateRepository,
) {
    fun throwIfBlocked(model: GeminiModel) {
        val now = LocalDateTime.now()
        val activeBlock = backoffStateRepository
            .findByModelNameAndBlockedUntilAfterOrderByBlockedUntilDesc(model.modelName, now)
            .firstOrNull()
            ?: return

        val retryAt = requireNotNull(activeBlock.blockedUntil)
        throw RateLimitExceededException(
            "Gemini calls are paused for model ${model.modelName} until $retryAt after ${activeBlock.limitType} rate limiting",
            activeBlock.limitType,
            model.modelName,
            retryAt,
        )
    }

    fun isBlocked(model: GeminiModel): Boolean =
        backoffStateRepository
            .findByModelNameAndBlockedUntilAfterOrderByBlockedUntilDesc(model.modelName, LocalDateTime.now())
            .isNotEmpty()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordRateLimit(exception: RateLimitExceededException) {
        val now = LocalDateTime.now()
        val state =
            backoffStateRepository.findByModelNameAndLimitType(exception.modelName, exception.limitType)
                ?: GeminiBackoffState(
                    modelName = exception.modelName,
                    limitType = exception.limitType,
                )

        state.consecutiveFailures += 1
        state.blockedUntil = exception.retryAt ?: calculateBlockedUntil(exception.limitType, state.consecutiveFailures, now)
        state.lastError = exception.message?.take(MAX_ERROR_LENGTH)
        state.updatedAt = now
        backoffStateRepository.save(state)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordSuccess(model: GeminiModel) {
        val states = backoffStateRepository.findByModelName(model.modelName)
        if (states.isEmpty()) return

        val now = LocalDateTime.now()
        states.forEach {
            it.consecutiveFailures = 0
            it.blockedUntil = null
            it.lastError = null
            it.updatedAt = now
        }
        backoffStateRepository.saveAll(states)
    }

    private fun calculateBlockedUntil(
        limitType: String,
        failures: Int,
        now: LocalDateTime,
    ): LocalDateTime =
        when (limitType) {
            "RPD" -> now.toLocalDate().plusDays(1).atStartOfDay()
            "RPM" -> now.plus(applyJitter(Duration.ofMinutes(1)))
            else -> now.plus(applyJitter(API_BACKOFFS[(failures - 1).coerceIn(0, API_BACKOFFS.lastIndex)]))
        }

    private fun applyJitter(baseDelay: Duration): Duration {
        if (baseDelay >= MAX_BACKOFF) return MAX_BACKOFF
        val jitterSeconds = ThreadLocalRandom.current().nextLong(0, 31)
        return baseDelay.plusSeconds(jitterSeconds).coerceAtMost(MAX_BACKOFF)
    }

    companion object {
        private val MAX_BACKOFF: Duration = Duration.ofDays(1)
        private val API_BACKOFFS: List<Duration> = listOf(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(6),
            MAX_BACKOFF,
        )
        private const val MAX_ERROR_LENGTH = 1000
    }
}
