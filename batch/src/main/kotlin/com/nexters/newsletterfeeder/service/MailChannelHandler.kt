package com.nexters.newsletterfeeder.service

import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.MessageHandler
import org.springframework.stereotype.Component

@Component
class MailChannelHandler(
    private val mailProcessor: MailProcessor,
) {
    @Bean
    @ServiceActivator(inputChannel = "mailChannel")
    fun mailMessageHandler(): MessageHandler {
        return MessageHandler { message ->
            val emailMessage = message.payload as EmailMessage
            mailProcessor.processEmail(emailMessage)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MailChannelHandler::class.java)
    }
}
