dependencies {
    implementation(libs.bundles.spring.boot.batch)
    implementation(libs.kotlin.stdlib.jdk8)

    // Jakarta Activation API for Spring Boot 3.x compatibility
    implementation(libs.jakarta.activation.api)

    // Logging
    implementation(libs.spring.boot.starter.logging)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.integration.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
