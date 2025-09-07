package com.nexters.newsletterfeeder.controller

import com.nexters.newsletterfeeder.config.AlarmIntegrationConfig.Companion.ALARM_TITLE
import com.nexters.newsletterfeeder.service.DailyNotificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/batch/notifications")
class NotificationController(
    private val dailyNotificationService: DailyNotificationService
) {
    @PostMapping("/send-single/manual")
    fun sendSingleManualNotification(
        @RequestParam(required = true) fcmToken: String,
        @RequestParam(required = true) deviceToken: String,
        @RequestParam(defaultValue = ALARM_TITLE) title: String,
    ): ResponseEntity<Map<String, Any>> {
        dailyNotificationService.sendSingleManualNotification(fcmToken, deviceToken, title)
        return ResponseEntity.ok(
            mapOf(
                "status" to "success",
                "message" to "특정 사용자에게 수동 알림이 단건 전송되었습니다.",
                "title" to title,
                "fcmToken" to fcmToken,
                "deviceToken" to deviceToken
            )
        )
    }
}
