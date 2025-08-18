dependencies {
    implementation(project(":newsletter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Firebase Admin SDK for push notifications
    implementation("com.google.firebase:firebase-admin:9.2.0")

    // For OG HTML page generation
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.h2)
}
