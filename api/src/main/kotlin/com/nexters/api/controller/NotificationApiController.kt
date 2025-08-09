package com.nexters.api.controller

import com.nexters.api.dto.ApiResponse
import com.nexters.api.dto.NotificationApiRequest
import com.nexters.api.dto.TokenRegistrationApiRequest
import com.nexters.external.service.PushNotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Notification", description = "푸시 알림 관련 API")
@RestController
@RequestMapping("/api/notifications")
class NotificationApiController(
    private val pushNotificationService: PushNotificationService
) {
    @Operation(summary = "FCM 토큰 등록", description = "사용자의 FCM 토큰을 등록합니다.")
    @PostMapping("/token")
    fun registerToken(
        @RequestBody request: TokenRegistrationApiRequest
    ): ResponseEntity<ApiResponse<String>> {
        val success =
            pushNotificationService.registerToken(
                deviceToken = request.deviceToken,
                fcmToken = request.fcmToken,
                deviceType = request.deviceType
            )

        return if (success) {
            ResponseEntity.ok(ApiResponse(true, "Token registered successfully"))
        } else {
            ResponseEntity.badRequest().body(ApiResponse(false, "Failed to register token"))
        }
    }

    @Operation(summary = "FCM 토큰 해제", description = "특정 디바이스 토큰을 해제합니다.")
    @DeleteMapping("/token")
    fun unregisterToken(
        @RequestParam fcmToken: String
    ): ResponseEntity<ApiResponse<String>> {
        val success = pushNotificationService.unregisterToken(fcmToken)

        return if (success) {
            ResponseEntity.ok(ApiResponse(true, "Token unregistered successfully"))
        } else {
            ResponseEntity.badRequest().body(ApiResponse(false, "Failed to unregister token"))
        }
    }

    @Operation(summary = "디바이스 토큰 해제", description = "특정 디바이스의 토큰을 해제합니다.")
    @DeleteMapping("/token/device/{deviceToken}")
    fun unregisterDeviceToken(
        @PathVariable deviceToken: String
    ): ResponseEntity<ApiResponse<String>> {
        val success = pushNotificationService.unregisterDeviceToken(deviceToken)

        return if (success) {
            ResponseEntity.ok(ApiResponse(true, "Device token unregistered successfully"))
        } else {
            ResponseEntity.badRequest().body(ApiResponse(false, "Failed to unregister device token"))
        }
    }

    @Operation(summary = "특정 디바이스에 알림 발송", description = "특정 디바이스에 푸시 알림을 발송합니다. (테스트용)")
    @PostMapping("/send/device/{deviceToken}")
    fun sendToDevice(
        @PathVariable deviceToken: String,
        @RequestBody request: NotificationApiRequest
    ): ResponseEntity<ApiResponse<String>> {
        val success =
            pushNotificationService.sendToDevice(
                deviceToken = deviceToken,
                title = request.title,
                body = request.body,
                data = request.data
            )

        return if (success) {
            ResponseEntity.ok(ApiResponse(true, "Notification sent successfully"))
        } else {
            ResponseEntity.badRequest().body(ApiResponse(false, "Failed to send notification"))
        }
    }

    @Operation(summary = "단일 토큰으로 알림 발송", description = "특정 토큰으로 직접 푸시 알림을 발송합니다. (테스트용)")
    @PostMapping("/send/token/{token}")
    fun sendToToken(
        @PathVariable token: String,
        @RequestBody request: NotificationApiRequest
    ): ResponseEntity<ApiResponse<String>> {
        val success =
            pushNotificationService.sendNotification(
                token = token,
                title = request.title,
                body = request.body,
                data = request.data
            )

        return if (success) {
            ResponseEntity.ok(ApiResponse(true, "Notification sent successfully"))
        } else {
            ResponseEntity.badRequest().body(ApiResponse(false, "Failed to send notification"))
        }
    }
}
