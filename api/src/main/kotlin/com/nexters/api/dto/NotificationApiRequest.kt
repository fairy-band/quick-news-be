package com.nexters.api.dto

import com.nexters.external.entity.DeviceType

data class TokenRegistrationApiRequest(
    val deviceToken: String,
    val fcmToken: String,
    val deviceType: DeviceType
)

data class NotificationApiRequest(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap()
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)
