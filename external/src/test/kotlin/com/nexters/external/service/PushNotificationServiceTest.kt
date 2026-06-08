package com.nexters.external.service

import com.nexters.external.repository.FcmTokenRepository
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PushNotificationServiceTest {
    private val fcmTokenRepository = mockk<FcmTokenRepository>(relaxed = true)

    @Test
    fun `sendNotification should skip Firebase when disabled`() {
        val service =
            PushNotificationService(
                fcmTokenRepository = fcmTokenRepository,
                firebaseEnabled = false,
            )

        val result =
            service.sendNotification(
                token = "fcm-token",
                title = "title",
                body = "body",
            )

        assertThat(result).isFalse()
        verify(exactly = 0) { fcmTokenRepository.deactivateToken(any()) }
    }
}
