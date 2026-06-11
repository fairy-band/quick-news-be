package com.nexters.api.batch.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.nexters.api.batch.dto.ContentDigestGroupingRequest
import com.nexters.api.batch.service.ContentDigestGroupingService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import kotlin.system.exitProcess

@Component
@ConditionalOnProperty(
    name = ["content.digest-grouping.runner.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class ContentDigestGroupingRunner(
    private val contentDigestGroupingService: ContentDigestGroupingService,
    private val environment: Environment,
    private val objectMapper: ObjectMapper,
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val exitCode =
            try {
                val request = readRequest()
                logger.info("Starting content digest grouping runner. request={}", request)

                val response = contentDigestGroupingService.groupContents(request)
                logger.info(
                    "Content digest grouping runner result:\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response),
                )

                0
            } catch (e: Exception) {
                logger.error("Content digest grouping runner failed", e)
                1
            }

        val generator = ExitCodeGenerator { exitCode }
        exitProcess(SpringApplication.exit(applicationContext, generator))
    }

    private fun readRequest(): ContentDigestGroupingRequest =
        ContentDigestGroupingRequest(
            dryRun = environment.getProperty(DRY_RUN_PROPERTY, Boolean::class.java, true),
            minCreatedAt = environment.getProperty(MIN_CREATED_AT_PROPERTY).toLocalDateTimeOrNull(),
            newsletterNames = environment.getProperty(NEWSLETTER_NAMES_PROPERTY).toNewsletterNameSet(),
            newsletterSourceIds = environment.getProperty(NEWSLETTER_SOURCE_IDS_PROPERTY).toNewsletterSourceIdSet(),
            minShortContentLength = environment.getProperty(MIN_SHORT_CONTENT_LENGTH_PROPERTY, Int::class.java, 500),
            minShortItems = environment.getProperty(MIN_SHORT_ITEMS_PROPERTY, Int::class.java, 3),
            minGroupContentLength = environment.getProperty(MIN_GROUP_CONTENT_LENGTH_PROPERTY, Int::class.java, 500),
            maxGroupContentLength = environment.getProperty(MAX_GROUP_CONTENT_LENGTH_PROPERTY, Int::class.java, 10_000),
        )

    private fun String?.toNewsletterNameSet(): Set<String>? =
        this
            ?.split(",")
            ?.map { name -> name.trim() }
            ?.filter { name -> name.isNotBlank() }
            ?.toSet()
            ?.takeIf { names -> names.isNotEmpty() }

    private fun String?.toNewsletterSourceIdSet(): Set<String>? =
        this
            ?.split(",")
            ?.map { id -> id.trim() }
            ?.filter { id -> id.isNotBlank() }
            ?.toSet()
            ?.takeIf { ids -> ids.isNotEmpty() }

    private fun String?.toLocalDateTimeOrNull(): LocalDateTime? =
        this
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { value -> LocalDateTime.parse(value) }

    companion object {
        private const val DRY_RUN_PROPERTY = "content.digest-grouping.runner.dry-run"
        private const val MIN_CREATED_AT_PROPERTY = "content.digest-grouping.runner.min-created-at"
        private const val NEWSLETTER_NAMES_PROPERTY = "content.digest-grouping.runner.newsletter-names"
        private const val NEWSLETTER_SOURCE_IDS_PROPERTY = "content.digest-grouping.runner.newsletter-source-ids"
        private const val MIN_SHORT_CONTENT_LENGTH_PROPERTY = "content.digest-grouping.runner.min-short-content-length"
        private const val MIN_SHORT_ITEMS_PROPERTY = "content.digest-grouping.runner.min-short-items"
        private const val MIN_GROUP_CONTENT_LENGTH_PROPERTY = "content.digest-grouping.runner.min-group-content-length"
        private const val MAX_GROUP_CONTENT_LENGTH_PROPERTY = "content.digest-grouping.runner.max-group-content-length"
    }
}
