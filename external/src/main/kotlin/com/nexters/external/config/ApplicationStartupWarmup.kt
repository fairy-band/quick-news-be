package com.nexters.external.config

import com.nexters.external.repository.DailyContentArchiveRepository
import com.nexters.external.repository.UserRepository
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Profile("prod", "dev")
class ApplicationStartupWarmup(
    private val jdbcTemplate: JdbcTemplate,
    private val mongoTemplate: MongoTemplate,
    private val userRepository: UserRepository,
    private val dailyContentArchiveRepository: DailyContentArchiveRepository,
) {
    @Value("\${app.warmup.enabled:true}")
    private var enabled: Boolean = true

    @EventListener(ApplicationReadyEvent::class)
    fun warmUp() {
        if (!enabled) {
            logger.info("startup_warmup_skipped reason=disabled")
            return
        }

        val timings = linkedMapOf<String, Long>()
        val startedAt = System.nanoTime()

        warmUpStep("postgresConnection", timings) {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
        }
        warmUpStep("userLoginPath", timings) {
            val latestDeviceToken =
                jdbcTemplate
                    .queryForList(
                        "SELECT device_token FROM users ORDER BY id DESC LIMIT 1",
                        String::class.java,
                    ).firstOrNull()

            if (!latestDeviceToken.isNullOrBlank()) {
                userRepository.findByDeviceToken(latestDeviceToken)
            }
        }
        warmUpStep("newsletterContentQueries", timings) {
            jdbcTemplate.queryForList(
                """
                SELECT e.id
                FROM exposure_contents e
                JOIN contents c ON c.id = e.content_id
                ORDER BY e.id DESC
                LIMIT 1
                """.trimIndent(),
            )
            jdbcTemplate.queryForList(
                """
                SELECT s.id
                FROM popular_newsletter_snapshots s
                WHERE s.segment_type = 'GLOBAL'
                    AND s.status = 'SUCCESS'
                    AND s.segment_key IS NULL
                    AND s.featured_exposure_content_id IS NOT NULL
                ORDER BY s.generated_at DESC
                LIMIT 1
                """.trimIndent(),
            )
        }
        warmUpStep("mongoConnection", timings) {
            mongoTemplate.executeCommand(Document("ping", 1))
        }
        warmUpStep("dailyArchiveRepository", timings) {
            dailyContentArchiveRepository.findByDateAndUserId(
                date = LocalDate.now(),
                userId = WARMUP_USER_ID,
            )
        }

        logger.info(
            "startup_warmup_completed totalMs={} timingsMs={}",
            (System.nanoTime() - startedAt) / 1_000_000,
            timings,
        )
    }

    private fun warmUpStep(
        name: String,
        timings: MutableMap<String, Long>,
        block: () -> Unit,
    ) {
        val startedAt = System.nanoTime()
        runCatching {
            block()
        }.onFailure { error ->
            logger.warn(
                "startup_warmup_step_failed step={} elapsedMs={}",
                name,
                (System.nanoTime() - startedAt) / 1_000_000,
                error,
            )
        }
        timings[name] = (System.nanoTime() - startedAt) / 1_000_000
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApplicationStartupWarmup::class.java)
        private const val WARMUP_USER_ID = -1L
    }
}
