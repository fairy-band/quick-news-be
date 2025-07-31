dependencies {
    implementation(project(":external"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("admin.jar")
}

tasks.jar {
    enabled = false
}
