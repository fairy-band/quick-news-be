package com.nexters.external.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "ai.gemini.content-analysis")
class AiGeminiContentAnalysisProperties {
    var minNaturalnessScore: Int = 7
    var maxRegenerationAttempts: Int = 1
}
