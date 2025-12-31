package com.nexters.external.service

import com.google.genai.types.GenerateContentResponse
import com.nexters.external.apiclient.GeminiClient
import com.nexters.external.dto.BatchContentItem
import com.nexters.external.dto.GeminiModel
import com.nexters.external.entity.GeminiRateLimit
import com.nexters.external.exception.RateLimitExceededException
import com.nexters.external.repository.GeminiRateLimitRepository
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Service
class GeminiRateLimiterService(
    private val rateLimitRepository: GeminiRateLimitRepository,
    private val geminiClient: GeminiClient,
    private val dailyLimitService: DailyLimitService,
) {
    private val logger = LoggerFactory.getLogger(GeminiRateLimiterService::class.java)

    private val rateLimiters = ConcurrentHashMap<String, RateLimiter>()

    fun executeWithRateLimit(
        inputKeywords: List<String>,
        model: GeminiModel,
        content: String,
    ): GenerateContentResponse? {
        // RPM 체크
        checkRpmLimit(model)

        // RPD 체크 및 증가 (트랜잭션 필요 - 별도 서비스 사용)
        dailyLimitService.incrementDailyLimit(model)

        // API 호출
        return geminiClient.requestContentAnalysis(inputKeywords, model, content)
    }

    /**
     * 배치 콘텐츠 분석을 Rate Limit 체크와 함께 실행합니다.
     * 여러 콘텐츠를 한 번의 API 호출로 처리하여 RPD(Requests Per Day) 제한을 효율적으로 관리합니다.
     *
     * @param inputKeywords 매칭할 키워드 목록
     * @param model 사용할 Gemini 모델
     * @param contentItems 분석할 콘텐츠 항목 리스트
     * @return GenerateContentResponse 또는 null
     */
    fun executeBatchWithRateLimit(
        inputKeywords: List<String>,
        model: GeminiModel,
        contentItems: List<BatchContentItem>,
    ): GenerateContentResponse? {
        // RPM 체크
        checkRpmLimit(model)

        // RPD 체크 및 증가 (1번의 API 호출이지만 여러 콘텐츠를 처리)
        dailyLimitService.incrementDailyLimit(model)

        // 배치 API 호출
        return geminiClient.requestBatchContentAnalysis(inputKeywords, model, contentItems)
    }

    private fun checkRpmLimit(model: GeminiModel) {
        val rateLimiter =
            rateLimiters.computeIfAbsent(model.modelName) {
                createRateLimiter(model)
            }

        val permission = rateLimiter.acquirePermission()
        if (!permission) {
            logger.warn("RPM limit exceeded for model: ${model.modelName} (limit: ${model.rpm})")
            throw RateLimitExceededException(
                "Rate limit exceeded: Maximum ${model.rpm} requests per minute for model ${model.modelName}",
                "RPM",
                model.modelName,
            )
        }
    }

    private fun createRateLimiter(model: GeminiModel): RateLimiter {
        val config =
            RateLimiterConfig
                .custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(model.rpm)
                .timeoutDuration(Duration.ofMillis(100))
                .build()

        return RateLimiter.of(model.modelName, config)
    }

    fun getAllTodayUsage(): Map<String, Pair<Int, Int>> {
        val today = LocalDate.now()
        val currentModelNames = GeminiModel.entries.map { it.modelName }.toSet()

        val rateLimits =
            rateLimitRepository
                .findAll()
                .filter { it.limitDate == today && it.modelName in currentModelNames }

        return rateLimits.associate {
            it.modelName to Pair(it.requestCount, it.maxRequestsPerDay)
        }
    }
}

@Service
class DailyLimitService(
    private val rateLimitRepository: GeminiRateLimitRepository,
) {
    private val logger = LoggerFactory.getLogger(DailyLimitService::class.java)

    @Transactional
    fun incrementDailyLimit(model: GeminiModel) {
        val today = LocalDate.now()

        val rateLimit =
            rateLimitRepository.findByModelNameAndLimitDate(model.modelName, today)
                ?: GeminiRateLimit(
                    modelName = model.modelName,
                    limitDate = today,
                    requestCount = 0,
                    maxRequestsPerDay = model.rpd,
                ).also {
                    rateLimitRepository.save(it)
                }

        if (rateLimit.requestCount >= rateLimit.maxRequestsPerDay) {
            logger.warn("RPD limit exceeded for model: ${model.modelName} (${rateLimit.requestCount}/${rateLimit.maxRequestsPerDay})")
            throw RateLimitExceededException(
                "Rate limit exceeded: Maximum ${rateLimit.maxRequestsPerDay} requests per day for model ${model.modelName}",
                "RPD",
                model.modelName,
            )
        }

        rateLimit.requestCount++
        rateLimitRepository.save(rateLimit)

        logger.info("Daily request count for ${model.modelName}: ${rateLimit.requestCount}/${rateLimit.maxRequestsPerDay}")
    }
}
