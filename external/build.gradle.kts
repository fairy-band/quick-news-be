tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.web)

    // Google Cloud Vertex AI
    implementation(libs.google.genai)

    // Gson for JSON parsing
    implementation(libs.gson)

    // MongoDB
    implementation(libs.spring.boot.starter.data.mongodb)

    // Logging
    implementation(libs.spring.boot.starter.logging)

    implementation(libs.micrometer.core)

    implementation(libs.firebase.admin)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.de.flapdoodle.embed.mongo.spring)
    testRuntimeOnly(libs.junit.platform.launcher)

    // JPA and Database dependencies
    api(libs.spring.boot.starter.data.jpa)
    implementation(libs.postgresql)
    testImplementation(libs.h2)

    // JSoup for HTML parsing
    implementation(libs.jsoup)

    // RSS Feed parsing
    implementation(libs.rome)
}
