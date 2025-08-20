package com.nexters.external.apiclient

import com.nexters.external.dto.DiscordWebhookRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class DiscordWebhookClient {
    private val logger = LoggerFactory.getLogger(DiscordWebhookClient::class.java)

    private val restClient = RestClient.builder().build()

    fun sendMessage(
        webhookUrl: String,
        request: DiscordWebhookRequest
    ): Boolean =
        try {
            val response =
                restClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity()

            if (response.statusCode.is2xxSuccessful) {
                logger.info("Discord webhook sent successfully to $webhookUrl")
                true
            } else {
                logger.error("Discord webhook failed with status ${response.statusCode}")
                false
            }
        } catch (e: RestClientException) {
            logger.error("RestClient error sending Discord webhook to $webhookUrl: ${e.message}", e)
            false
        } catch (e: Exception) {
            logger.error("Unexpected error sending Discord webhook to $webhookUrl", e)
            false
        }
}
