tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

dependencies {
    api(project(":external"))
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.aop)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)

    // JPA and Database dependencies
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.postgresql)
    testImplementation(libs.h2)

    // JSoup for HTML parsing
    implementation(libs.jsoup)
}
