package com.nexters.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.io.File
import java.io.FileInputStream
import javax.annotation.PostConstruct

@Configuration
@Profile("prod")
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
                val serviceAccountFile = File(firebaseServiceAccountKeyPath)
                if (!serviceAccountFile.exists()) {
                    throw RuntimeException("Firebase service account key file not found at: $firebaseServiceAccountKeyPath")
                }

                logger.info("Firebase 초기화 중...")

                val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountFile))

                val options =
                    FirebaseOptions
                        .builder()
                        .setCredentials(credentials)
                        .setProjectId(firebaseProjectId)
                        .build()

                FirebaseApp.initializeApp(options)
                logger.info("Firebase Admin SDK 초기화 완료 - 프로젝트: $firebaseProjectId")
            }
        } catch (e: Exception) {
            logger.error("Firebase 초기화 실패", e)
            throw RuntimeException("Firebase 초기화 실패", e)
        }
    }
}
