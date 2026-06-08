dependencies {
    implementation(project(":newsletter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.jackson2)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Firebase Admin SDK for push notifications
    implementation(libs.firebase.admin)

    // Batch 모듈 기능 통합
    implementation(libs.bundles.spring.boot.batch)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.jakarta.activation.api)
    implementation(libs.spring.boot.starter.logging)
    implementation(libs.caffeine)
    implementation(libs.jsoup)
    implementation(libs.google.analytics.data)
    implementation(libs.google.auth.oauth2)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.integration.test)
    testImplementation(libs.google.genai)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.h2)
}
