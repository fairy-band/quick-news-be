dependencies {
    implementation(project(":newsletter"))
    implementation(libs.bundles.spring.boot.batch)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.kotlin.stdlib.jdk8)

    // Jakarta Activation API for Spring Boot 3.x compatibility
    implementation(libs.jakarta.activation.api)

    // Logging
    implementation(libs.spring.boot.starter.logging)

    // Firebase Admin SDK for FCM
    implementation(libs.firebase.admin)

    // JSoup for HTML parsing
    implementation(libs.jsoup)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.integration.test)
    testImplementation(libs.google.genai) // Needed for mocking GenerateContentResponse
    testRuntimeOnly(libs.junit.platform.launcher)
}
