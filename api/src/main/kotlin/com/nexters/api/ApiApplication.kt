package com.nexters.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.integration.annotation.IntegrationComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    scanBasePackages = [
        "com.nexters.api",
        "com.nexters.external",
        "com.nexters.newsletter",
    ]
)
@EnableScheduling
@IntegrationComponentScan
@ConfigurationPropertiesScan
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
