package com.nexters.newsletter.resolver

import com.nexters.external.entity.User
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class OnboardingContentPolicy {
    fun resolveExposureContentIds(
        user: User,
        date: LocalDate,
    ): List<Long> {
        if (user.isOnboarded) {
            return emptyList()
        }

        return when (ChronoUnit.DAYS.between(user.createdAt.toLocalDate(), date)) {
            0L -> DAY0_EXPOSURE_CONTENT_IDS
            else -> emptyList()
        }
    }

    companion object {
        private val DAY0_EXPOSURE_CONTENT_IDS =
            listOf(
                6533L, // BE
                6764L, // FE
                6652L, // Android
                8862L, // iOS
                211L, // DevOps
            )
    }
}
