package com.nexters.external.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.UnsynchronizedAppenderBase
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Discord Webhook Appender for Logback
 *
 * 직접 구현한 Discord Webhook Appender입니다.
 * ERROR 레벨의 로그를 Discord webhook으로 전송합니다.
 */
class DiscordWebhookAppender : UnsynchronizedAppenderBase<ILoggingEvent>() {
    // Discord webhook URL
    var webhookUri: String? = null

    // Discord 메시지 설정
    var username: String = "Newsletter Feeder"
    var avatarUrl: String = "https://cdn.discordapp.com/embed/avatars/0.png"

    // HTTP Client
    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    // JSON Mapper
    private val objectMapper = ObjectMapper()

    override fun append(event: ILoggingEvent) {
        if (webhookUri.isNullOrBlank()) {
            addError("No webhookUri set for the appender named [$name]")
            return
        }

        try {
            val payload = createDiscordPayload(event)
            sendToDiscord(payload)
        } catch (e: Exception) {
            addError("Error sending log to Discord", e)
        }
    }

    private fun createDiscordPayload(event: ILoggingEvent): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        val loggerName = event.loggerName
        val message = event.formattedMessage
        val level = event.level.toString()
        val threadName = event.threadName

        // 예외 정보 추출
        val throwableInfo =
            if (event.throwableProxy != null) {
                "\n\n**Stack Trace:**\n```\n${ThrowableProxyUtil.asString(event.throwableProxy)}\n```"
            } else {
                ""
            }

        // Discord Embed 생성
        val color =
            when (level) {
                "ERROR" -> 16711680 // 빨강
                "WARN" -> 16776960 // 노랑
                "INFO" -> 65280 // 초록
                else -> 8421504 // 회색
            }

        val embed =
            mapOf(
                "title" to "[$level] $loggerName",
                "description" to "$message$throwableInfo",
                "color" to color,
                "footer" to mapOf("text" to "Thread: $threadName"),
                "timestamp" to timestamp
            )

        // Discord Webhook 메시지 생성
        val webhookMessage =
            mapOf(
                "username" to username,
                "avatar_url" to avatarUrl,
                "embeds" to listOf(embed)
            )

        return objectMapper.writeValueAsString(webhookMessage)
    }

    private fun sendToDiscord(payload: String) {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(webhookUri!!))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()

        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                addError("Discord API returned non-2xx response: ${response.statusCode()}, body: ${response.body()}")
            }
        } catch (e: IOException) {
            addError("Error sending log to Discord", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            addError("Interrupted while sending log to Discord", e)
        }
    }
}
