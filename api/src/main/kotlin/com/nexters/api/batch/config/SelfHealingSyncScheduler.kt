package com.nexters.api.batch.config

import com.nexters.api.batch.service.ContentAiProcessingService
import com.nexters.api.batch.service.ExposureContentMarkdownAiService
import com.nexters.external.apiclient.EmbeddingServiceClient
import com.nexters.external.repository.ContentRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 파이프라인 자가 치유(Self-Healing) 스케줄러
 * - 30분 간격(20분, 50분)으로 실행되어 일시적 네트워크 에러나 API 오류로 누락된 파이프라인 산출물을 자동 복구합니다.
 * 1. 마크다운 누락 항목 백필
 * 2. 임베딩(Embedding) 누락 항목 백필
 * 3. 미처리 요약(Summary/Exposure) 버퍼 경과분 백필
 */
@Component
@ConditionalOnProperty(
    name = ["batch.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class SelfHealingSyncScheduler(
    private val contentRepository: ContentRepository,
    private val embeddingServiceClient: EmbeddingServiceClient,
    private val exposureContentMarkdownAiService: ExposureContentMarkdownAiService,
    private val contentAiProcessingService: ContentAiProcessingService,
) {
    private val logger = LoggerFactory.getLogger(SelfHealingSyncScheduler::class.java)
    private val isHealing = AtomicBoolean(false)

    @Scheduled(cron = "0 20,50 * * * *")
    fun performSelfHealing() {
        if (!isHealing.compareAndSet(false, true)) {
            logger.warn("Self-healing task is already running. Skipping this cycle.")
            return
        }

        try {
            logger.info("=== 🩺 Starting Automated Pipeline Self-Healing Cycle ===")

            // Phase 1: 누락된 ExposureContentMarkdown 자동 생성 트리거
            healMissingMarkdowns()

            // Phase 2: 누락된 bge-m3 임베딩 자동 복구
            healMissingEmbeddings()

            // Phase 3: 미처리된 Content AI 요약 배치 트리거
            healUnprocessedContents()

            logger.info("=== 🩺 Pipeline Self-Healing Cycle Completed Successfully ===")
        } catch (e: Exception) {
            logger.error("Error during self-healing cycle", e)
        } finally {
            isHealing.set(false)
        }
    }

    private fun healMissingMarkdowns() {
        runCatching {
            logger.info("[Self-Healing] Checking for missing Exposure Content Markdowns...")
            exposureContentMarkdownAiService.processUnprocessedMarkdowns()
        }.onFailure { e ->
            logger.warn("[Self-Healing] Failed to heal missing markdowns: {}", e.message)
        }
    }

    private fun healMissingEmbeddings() {
        runCatching {
            logger.info("[Self-Healing] Checking for missing Content Embeddings...")
            val missingIds = contentRepository.findExposureContentsMissingEmbedding(limit = EMBEDDING_BATCH_LIMIT)
            if (missingIds.isEmpty()) {
                logger.info("[Self-Healing] All exposure contents have embeddings. Nothing to heal.")
                return@runCatching
            }

            logger.info("[Self-Healing] Found {} contents missing embeddings. Starting recovery...", missingIds.size)
            var successCount = 0
            for (id in missingIds) {
                try {
                    embeddingServiceClient.embedContent(id)
                    successCount++
                } catch (e: Exception) {
                    logger.warn("[Self-Healing] Failed to embed content ID {}: {}", id, e.message)
                }
            }
            logger.info("[Self-Healing] Embedding recovery completed: {}/{} healed successfully.", successCount, missingIds.size)
        }.onFailure { e ->
            logger.warn("[Self-Healing] Error during embedding recovery phase: {}", e.message)
        }
    }

    private fun healUnprocessedContents() {
        runCatching {
            logger.info("[Self-Healing] Checking for unprocessed contents...")
            val result = contentAiProcessingService.processUnprocessedContents()
            if (result.processedCount > 0) {
                logger.info("[Self-Healing] Processed {} unprocessed contents in self-healing phase.", result.processedCount)
            }
        }.onFailure { e ->
            logger.warn("[Self-Healing] Error during content AI processing phase: {}", e.message)
        }
    }

    companion object {
        private const val EMBEDDING_BATCH_LIMIT = 50
    }
}
