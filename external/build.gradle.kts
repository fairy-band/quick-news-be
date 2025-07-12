tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.spring.boot.starter)

    // Google Cloud Vertex AI
    implementation(libs.google.genai)

    // Gson for JSON parsing
    implementation(libs.gson)

    // Logging
    implementation(libs.spring.boot.starter.logging)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}
