dependencies {
    implementation(project(":external"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.integration)
    implementation(libs.spring.boot.starter.actuator)
    
    // Spring Integration Mail 명시적 추가
    implementation(libs.spring.integration.mail)
    
    // JPA 의존성 추가
    implementation(libs.spring.boot.starter.data.jpa)
    implementation("org.postgresql:postgresql")
    
    implementation(libs.kotlin.stdlib.jdk8)

    // Jakarta Activation API for Spring Boot 3.x compatibility
    implementation(libs.jakarta.activation.api)

    // Logging
    implementation(libs.spring.boot.starter.logging)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.integration.test)
    testImplementation(libs.google.genai) // Needed for mocking GenerateContentResponse
    testRuntimeOnly(libs.junit.platform.launcher)
}
