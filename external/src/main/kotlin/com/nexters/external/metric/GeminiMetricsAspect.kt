package com.nexters.external.metric

import com.google.genai.types.GenerateContentResponse
import com.nexters.external.dto.GeminiModel
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class GeminiMetricsAspect(
    private val geminiMetricsService: GeminiMetricsService,
) {
    @Around("execution(* com.nexters.external.apiclient.GeminiClient.requestKeywords(..))")
    fun recordKeywordsMetrics(joinPoint: ProceedingJoinPoint): GenerateContentResponse? {
        val args = joinPoint.args
        val model = args[1] as GeminiModel

        return geminiMetricsService.measureApiCall(model, "keywords") {
            joinPoint.proceed() as GenerateContentResponse?
        }
    }

    @Around("execution(* com.nexters.external.apiclient.GeminiClient.requestSummary(..))")
    fun recordSummaryMetrics(joinPoint: ProceedingJoinPoint): GenerateContentResponse? {
        val args = joinPoint.args
        val model = args[0] as GeminiModel

        return geminiMetricsService.measureApiCall(model, "summary") {
            joinPoint.proceed() as GenerateContentResponse?
        }
    }
}
