dependencies {
    implementation(project(":newsletter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Firebase Admin SDK for push notifications
    implementation(libs.firebase.admin)

    // For OG HTML page generation
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Batch 모듈 기능 통합
    implementation(libs.bundles.spring.boot.batch)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.jakarta.activation.api)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.jsoup)
    implementation("com.google.analytics:google-analytics-data:0.50.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.integration.test)
    testImplementation(libs.google.genai)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.h2)
}
