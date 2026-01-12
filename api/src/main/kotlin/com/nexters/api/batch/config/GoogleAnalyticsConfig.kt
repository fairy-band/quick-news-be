package com.nexters.api.batch.config

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient
import com.google.analytics.data.v1beta.BetaAnalyticsDataSettings
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.io.FileInputStream

@Configuration
class GoogleAnalyticsConfig {
    private val logger = LoggerFactory.getLogger(GoogleAnalyticsConfig::class.java)

    @Value("\${google.analytics.credentials.path}")
    private lateinit var credentialsPath: String

    @Bean
    fun betaAnalyticsDataClient(): BetaAnalyticsDataClient {
        try {
            logger.info("Google Analytics 클라이언트 초기화 중...")

            val credentials = loadCredentials()

            val settings =
                BetaAnalyticsDataSettings
                    .newBuilder()
                    .setCredentialsProvider { credentials }
                    .build()

            val client = BetaAnalyticsDataClient.create(settings)
            logger.info("Google Analytics 클라이언트 초기화 완료")

            return client
        } catch (e: Exception) {
            logger.error("Google Analytics 클라이언트 초기화 실패", e)
            throw RuntimeException("Google Analytics 클라이언트 초기화 실패", e)
        }
    }

    private fun loadCredentials(): GoogleCredentials {
        // Classpath에서 리소스 로드 (resources 디렉토리 안의 파일)
        val credentialsResource = ClassPathResource(credentialsPath)

        return if (!credentialsResource.exists()) {
            // Classpath에서 찾을 수 없으면, 절대 경로로 시도
            val credentialsFile = File(credentialsPath)
            if (!credentialsFile.exists()) {
                throw RuntimeException(
                    "Google Analytics credentials file not found - " +
                        "Classpath: $credentialsPath, " +
                        "File: ${credentialsFile.absolutePath}"
                )
            }
            logger.info("Google Analytics 서비스 계정 키를 파일 시스템에서 로드: ${credentialsFile.absolutePath}")
            GoogleCredentials
                .fromStream(FileInputStream(credentialsFile))
                .createScoped(listOf("https://www.googleapis.com/auth/analytics.readonly"))
        } else {
            logger.info("Google Analytics 서비스 계정 키를 classpath에서 로드: $credentialsPath")
            GoogleCredentials
                .fromStream(credentialsResource.inputStream)
                .createScoped(listOf("https://www.googleapis.com/auth/analytics.readonly"))
        }
    }
}
