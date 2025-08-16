package com.nexters.newsletterfeeder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.integration.annotation.IntegrationComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@IntegrationComponentScan
@ConfigurationPropertiesScan
@ComponentScan(basePackages = ["com.nexters.newsletterfeeder", "com.nexters.external", "com.nexters.newsletter"])
class NewsletterFeederApplication

fun main(args: Array<String>) {
    runApplication<NewsletterFeederApplication>(*args)
}
