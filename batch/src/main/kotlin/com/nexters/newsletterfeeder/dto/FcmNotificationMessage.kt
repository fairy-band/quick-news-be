package com.nexters.newsletterfeeder.dto

import java.util.UUID

data class FcmNotificationMessage(
    val user: FcmUser,
    val title: String,
    val notificationType: NotificationType = NotificationType.DAILY,
    val timestamp: Long = System.currentTimeMillis(),
)

data class FcmUser(
    val deviceToken: String,
    val fcmToken: String,
)

enum class NotificationType {
    DAILY, // 일일 정기 알림
    MANUAL, // 수동 알림
}

data class FcmNotificationResult(
    val fcmToken: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class BatchFcmRequest(
    val users: List<FcmUser>,
    val title: String,
    val notificationType: NotificationType = NotificationType.DAILY,
    val requestId: String = UUID.randomUUID().toString(),
)
