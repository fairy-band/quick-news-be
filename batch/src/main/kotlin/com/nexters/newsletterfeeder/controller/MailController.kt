package com.nexters.newsletterfeeder.controller

import com.nexters.newsletterfeeder.service.MailTriggerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailController(
    private val mailTriggerService: MailTriggerService
) {
    @PostMapping("/read")
    fun triggerMailReading(): ResponseEntity<Map<String, String>> {
        mailTriggerService.triggerManualMailReading()
        return ResponseEntity.ok(
            mapOf(
                "status" to "success",
                "message" to "메일 읽기 작업이 트리거되었습니다.",
                "timestamp" to System.currentTimeMillis().toString()
            )
        )
    }

    @GetMapping("/status")
    fun getMailStatus(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "status" to "active",
                "service" to "Newsletter Integration",
                "description" to "Spring Integration Kotlin DSL 기반 메일 처리 서비스",
                "timestamp" to System.currentTimeMillis()
            )
        )
}
