dependencies {
    implementation(project(":newsletter"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // Kotlin logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("admin.jar")
}

tasks.jar {
    enabled = false
}
