package com.nexters.newsletterfeeder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = [
    "com.nexters.newsletterfeeder",
    "com.nexters.external"
])
class NewsletterFeederApplication

fun main(args: Array<String>) {
    runApplication<NewsletterFeederApplication>(*args)
}
