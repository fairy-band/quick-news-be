package com.nexters.api.batch.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.nexters.api.batch.dto.NewsletterParseOnlyBackfillRequest
import com.nexters.api.batch.service.NewsletterParseOnlyBackfillService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import kotlin.system.exitProcess

@Component
@ConditionalOnProperty(
    name = ["newsletter.backfill.parse-only-runner.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class NewsletterParseOnlyBackfillRunner(
    private val newsletterParseOnlyBackfillService: NewsletterParseOnlyBackfillService,
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val exitCode =
            try {
                val request = readRequest()
                logger.info("Starting newsletter parse-only backfill runner. request={}", request)

                val response = newsletterParseOnlyBackfillService.createContents(request)
                logger.info(
                    "Newsletter parse-only backfill runner result:\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response),
                )

                0
            } catch (e: Exception) {
                logger.error("Newsletter parse-only backfill runner failed", e)
                1
            }

        val generator = ExitCodeGenerator { exitCode }
        exitProcess(SpringApplication.exit(applicationContext, generator))
    }

    private fun readRequest(): NewsletterParseOnlyBackfillRequest =
        NewsletterParseOnlyBackfillRequest(
            dryRun = environment.getProperty(DRY_RUN_PROPERTY, Boolean::class.java, true),
            limit = environment.getProperty(LIMIT_PROPERTY, Int::class.java),
            force = environment.getProperty(FORCE_PROPERTY, Boolean::class.java, false),
            senderEmails = environment.getProperty(SENDER_EMAILS_PROPERTY).toSenderEmailSet(),
        )

    private fun String?.toSenderEmailSet(): Set<String>? =
        this
            ?.split(",")
            ?.map { sender -> sender.trim() }
            ?.filter { sender -> sender.isNotBlank() }
            ?.toSet()
            ?.takeIf { senders -> senders.isNotEmpty() }

    companion object {
        private const val DRY_RUN_PROPERTY = "newsletter.backfill.parse-only-runner.dry-run"
        private const val LIMIT_PROPERTY = "newsletter.backfill.parse-only-runner.limit"
        private const val FORCE_PROPERTY = "newsletter.backfill.parse-only-runner.force"
        private const val SENDER_EMAILS_PROPERTY = "newsletter.backfill.parse-only-runner.sender-emails"
    }
}
