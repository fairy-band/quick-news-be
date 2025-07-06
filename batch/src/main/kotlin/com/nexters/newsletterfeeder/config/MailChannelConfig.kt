package com.nexters.newsletterfeeder.config

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.core.MessageSource
import org.springframework.integration.mail.MailReceivingMessageSource
import org.springframework.integration.mail.Pop3MailReceiver
import org.springframework.messaging.MessageChannel
import java.net.URLEncoder
import java.util.Properties

@Configuration
@EnableIntegration
class MailChannelConfig(val mailProperties: MailProperties) {
    @Bean
    fun mailMessageSource(): MessageSource<*> {
        val protocol = mailProperties.protocol
        val userName = mailProperties.userName
        val password = mailProperties.password
        val host = mailProperties.host
        val port = mailProperties.port
        val authenticator =
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(userName, password)
                }
            }
        // SSL 설정을 위한 Properties 객체 생성
        val properties =
            Properties().apply {
                setProperty("mail.store.protocol", "pop3s")
                setProperty("mail.pop3s.host", host)
                setProperty("mail.pop3s.port", port.toString())
                setProperty("mail.pop3s.ssl.enable", "true")
                setProperty("mail.pop3s.ssl.trust", "*")
                setProperty("mail.pop3s.ssl.protocols", "TLSv1.2")
                setProperty("mail.pop3s.connectiontimeout", "10000")
                setProperty("mail.pop3s.timeout", "10000")
                setProperty("mail.pop3s.writetimeout", "10000")
                setProperty("mail.debug", "false")
            }

        return MailReceivingMessageSource(
            Pop3MailReceiver("$protocol://${URLEncoder.encode(userName, "UTF-8")}:$password@$host/INBOX")
                .apply {
                    setShouldDeleteMessages(false) // 기본 POP3 프로토콜은 메일을 삭제한다. 옵션을 켜서 메일을 삭제하지 않는다.
                    setMaxFetchSize(DEFAULT_FETCH_MAIL_SIZE)
                    setJavaMailAuthenticator(authenticator)
                    setProtocol(protocol)
                    setJavaMailProperties(properties)
                }
        )
    }

    @Bean
    fun mailChannel(): MessageChannel {
        return PublishSubscribeChannel()
    }

    companion object {
        private const val DEFAULT_FETCH_MAIL_SIZE = 10
    }
}
