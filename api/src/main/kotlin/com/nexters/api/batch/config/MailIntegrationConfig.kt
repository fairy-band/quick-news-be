package com.nexters.api.batch.config

import com.nexters.api.batch.dto.EmailMessage
import com.nexters.api.batch.service.MailProcessor
import com.nexters.api.batch.service.MailReader
import com.nexters.external.entity.NewsletterSource
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.IntegrationFlow.from
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.mail.MailReceivingMessageSource
import org.springframework.integration.mail.Pop3MailReceiver
import org.springframework.integration.support.MessageBuilder
import org.springframework.messaging.MessageChannel
import java.net.URLEncoder
import java.util.Properties
import kotlin.toString

@Configuration
@EnableIntegration
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MailIntegrationConfig(
    private val mailProperties: MailProperties,
    private val mailProcessor: MailProcessor,
) {
    private val logger = LoggerFactory.getLogger(MailIntegrationConfig::class.java)

    // 채널 정의
    @Bean
    fun mailInputChannel(): MessageChannel = DirectChannel()

    @Bean
    fun mailOutputChannel(): MessageChannel = PublishSubscribeChannel()

    @Bean
    fun mailErrorChannel(): MessageChannel = PublishSubscribeChannel()

    @Bean
    fun scheduleTriggerChannel(): MessageChannel = DirectChannel()

    @Bean
    fun mailSaveChannel(): MessageChannel = DirectChannel()

    @Bean
    fun mailSavedChannel(): MessageChannel = PublishSubscribeChannel()

    @Bean
    fun scheduledTriggerFlow(
        scheduleTriggerChannel: MessageChannel,
        mailInputChannel: MessageChannel,
        mailOutputChannel: MessageChannel,
    ): IntegrationFlow =
        integrationFlow(scheduleTriggerChannel) {
            handle<Any> { payload, _ ->
                logger.info("스케줄링된 메일 읽기 시작: $payload")

                // 주입된 mailInputChannel로 메시지 전송
                mailInputChannel.send(
                    MessageBuilder
                        .withPayload("TRIGGER_MAIL_READING")
                        .setHeader("source", "scheduler")
                        .build(),
                )

                "메일 읽기 요청 전송 완료"
            }

            channel(mailOutputChannel)
        }

    @Bean
    fun mailInboundFlow(
        mailReader: MailReader,
        mailInputChannel: MessageChannel,
        mailErrorChannel: MessageChannel,
        mailSaveChannel: MessageChannel,
    ): IntegrationFlow =
        integrationFlow(mailInputChannel) {
            transform<Any> { _ ->
                logger.info("메일 수신 시작")
                // MailReader를 사용하여 메일 수신
                mailReader.readMails()
            }

            split()

            // 메일 검증 및 전처리
            handle<EmailMessage> { payload, headers ->
                try {
                    logger.info("메일 전처리: ${payload.subject}")
                    // 여기서 필요한 전처리 로직 수행 (필터링, 검증 등)

                    // 메일 저장 채널로 전달
                    payload
                } catch (e: Exception) {
                    logger.error("메일 전처리 중 오류 발생: ${payload.subject}", e)
                    // 에러 채널로 전송
                    mailErrorChannel.send(
                        MessageBuilder
                            .withPayload(e)
                            .copyHeaders(headers)
                            .setHeader("failedSubject", payload.subject)
                            .setHeader("stage", "preprocessing")
                            .build(),
                    )
                }
            }

            // 메일 저장 채널로 라우팅
            channel(mailSaveChannel)
        }

    @Bean
    fun mailSaveFlow(
        mailSaveChannel: MessageChannel,
        mailSavedChannel: MessageChannel,
        mailErrorChannel: MessageChannel
    ): IntegrationFlow =
        integrationFlow(mailSaveChannel) {
            handle<EmailMessage> { payload, headers ->
                try {
                    logger.info("메일 저장: ${payload.subject}")

                    val result = mailProcessor.saveNewsletterSource(payload)

                    MessageBuilder
                        .withPayload(result)
                        .copyHeaders(headers)
                        .setHeader("savedSubject", payload.subject)
                        .setHeader("savedTimestamp", System.currentTimeMillis())
                        .build()
                } catch (e: Exception) {
                    logger.error("메일 저장 중 오류 발생: ${payload.subject}", e)
                    // 에러 채널로 전송
                    mailErrorChannel.send(
                        MessageBuilder
                            .withPayload(e)
                            .copyHeaders(headers)
                            .setHeader("failedSubject", payload.subject)
                            .setHeader("stage", "saving")
                            .build()
                    )
                }
            }

            // 저장 결과 알림 채널로 전송
            channel(mailSavedChannel)
        }

    // 메일 저장 결과 처리 Flow
    @Bean
    fun mailSavedFlow(
        mailSavedChannel: MessageChannel,
        mailOutputChannel: MessageChannel
    ): IntegrationFlow =
        integrationFlow(mailSavedChannel) {
            handle<NewsletterSource> { payload, headers ->
                val subject = headers["savedSubject"] ?: "알 수 없는 제목"
                logger.info("메일 저장 완료: $subject")
                mailProcessor.processNewsletterSource(payload.id!!)
                payload
            }

            channel(mailOutputChannel)
        }

    // 에러 처리 IntegrationFlow
    @Bean
    fun errorHandlingFlow(): IntegrationFlow =
        integrationFlow {
            from("mailErrorChannel")

            handle<Exception> { exception, headers ->
                val subject = headers["failedSubject"] ?: "알 수 없는 제목"
                val stage = headers["stage"] ?: "unknown"
                logger.error("메일 처리 중 오류 발생 - 단계: $stage, 제목: $subject, 오류: ${exception.message}", exception)
            }
        }

    private fun mailReceiverUrl(): String {
        val protocol = mailProperties.protocol
        val userName = URLEncoder.encode(mailProperties.username, "UTF-8")
        val password = mailProperties.password
        val host = mailProperties.host

        return "$protocol://$userName:$password@$host/INBOX"
    }

    private fun mailAuthenticator(): Authenticator =
        object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(mailProperties.username, mailProperties.password)
        }

    private fun mailProperties(): Properties =
        Properties().apply {
            setProperty("mail.store.protocol", "pop3s")
            setProperty("mail.pop3s.host", mailProperties.host)
            setProperty("mail.pop3s.port", mailProperties.port.toString())
            setProperty("mail.pop3s.ssl.enable", "true")
            setProperty("mail.pop3s.ssl.trust", "*")
            setProperty("mail.pop3s.ssl.protocols", "TLSv1.2")
            setProperty("mail.pop3s.connectiontimeout", "10000")
            setProperty("mail.pop3s.timeout", "10000")
            setProperty("mail.pop3s.writetimeout", "10000")
            setProperty("mail.debug", "false")
        }

    // Configuration 내부의 빈 생성 메서드 호출은 프록시에 의해 재사용됨
    @Bean
    fun mailReader(): MailReader = MailReader(mailMessageSource())

    @Bean
    fun mailMessageSource() =
        MailReceivingMessageSource(
            Pop3MailReceiver(mailReceiverUrl()).apply {
                setShouldDeleteMessages(false)
                setMaxFetchSize(MAX_FETCH_SIZE)
                setJavaMailAuthenticator(mailAuthenticator())
                setJavaMailProperties(mailProperties())
            },
        )

    companion object {
        private const val MAX_FETCH_SIZE = 10
    }
}
