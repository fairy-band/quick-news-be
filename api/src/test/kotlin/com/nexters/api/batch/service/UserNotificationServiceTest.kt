package com.nexters.api.batch.service

import com.nexters.api.batch.dto.BatchFcmRequest
import com.nexters.api.batch.dto.NotificationType
import com.nexters.external.entity.DeviceType
import com.nexters.external.entity.FcmToken
import com.nexters.external.repository.FcmTokenRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import kotlin.test.assertEquals

class UserNotificationServiceTest {
    private val fcmBatchTriggerChannel = mockk<MessageChannel>()
    private val fcmInputChannel = mockk<MessageChannel>(relaxed = true)
    private val fcmTokenRepository = mockk<FcmTokenRepository>()

    private val sut =
        UserNotificationService(
            fcmBatchTriggerChannel = fcmBatchTriggerChannel,
            fcmInputChannel = fcmInputChannel,
            fcmTokenRepository = fcmTokenRepository,
        )

    @Test
    fun `sendWeeklyNotification should send weekly batch request`() {
        val messageSlot = slot<Message<*>>()
        every { fcmBatchTriggerChannel.send(capture(messageSlot)) } returns true
        every { fcmTokenRepository.findAllByIsActiveTrue() } returns
            listOf(
                FcmToken(
                    deviceToken = "device-token",
                    fcmToken = "fcm-token",
                    deviceType = DeviceType.IOS,
                ),
            )

        sut.sendWeeklyNotification()

        val request = messageSlot.captured.payload as BatchFcmRequest
        assertEquals(NotificationType.WEEKLY, request.notificationType)
        assertEquals("device-token", request.users.single().deviceToken)
        assertEquals("fcm-token", request.users.single().fcmToken)
        assertEquals("scheduled_weekly", messageSlot.captured.headers["source"])
        verify(exactly = 1) { fcmBatchTriggerChannel.send(any()) }
    }
}
