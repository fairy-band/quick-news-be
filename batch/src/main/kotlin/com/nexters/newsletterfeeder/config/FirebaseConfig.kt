package com.nexters.newsletterfeeder.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.io.FileInputStream
import javax.annotation.PostConstruct

@Configuration
class FirebaseConfig {
    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Value("\${firebase.service-account-key-path:firebase-service-account.json}")
    private val firebaseServiceAccountKeyPath: String = ""

    @Value("\${firebase.project-id}")
    private val firebaseProjectId: String = ""

    @PostConstruct
    fun initializeFirebase() {
        try {
            // 이미 초기화되어 있는지 확인
            if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Firebase 초기화 중...")

                // Classpath에서 리소스 로드 (resources 디렉토리 안의 파일)
                val serviceAccountResource = ClassPathResource(firebaseServiceAccountKeyPath)

                if (!serviceAccountResource.exists()) {
                    // Classpath에서 찾을 수 없으면, 절대 경로로 시도
                    val serviceAccountFile = File(firebaseServiceAccountKeyPath)
                    if (!serviceAccountFile.exists()) {
                        throw RuntimeException(
                            "Firebase service account key file not found - " +
                                "Classpath: $firebaseServiceAccountKeyPath, " +
                                "File: ${serviceAccountFile.absolutePath}"
                        )
                    }
                    logger.info("Firebase 서비스 계정 키를 파일 시스템에서 로드: ${serviceAccountFile.absolutePath}")
                    val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountFile))

                    val options =
                        FirebaseOptions
                            .builder()
                            .setCredentials(credentials)
                            .setProjectId(firebaseProjectId)
                            .build()

                    FirebaseApp.initializeApp(options)
                } else {
                    logger.info("Firebase 서비스 계정 키를 classpath에서 로드: $firebaseServiceAccountKeyPath")
                    val credentials = GoogleCredentials.fromStream(serviceAccountResource.inputStream)

                    val options =
                        FirebaseOptions
                            .builder()
                            .setCredentials(credentials)
                            .setProjectId(firebaseProjectId)
                            .build()

                    FirebaseApp.initializeApp(options)
                }

                logger.info("Firebase Admin SDK 초기화 완료 - 프로젝트: $firebaseProjectId")
            }
        } catch (e: Exception) {
            logger.error("Firebase 초기화 실패", e)
            throw RuntimeException("Firebase 초기화 실패", e)
        }
    }
}
