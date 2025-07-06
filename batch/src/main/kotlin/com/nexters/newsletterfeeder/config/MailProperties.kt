package com.nexters.newsletterfeeder.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.mail")
data class MailProperties(
    val host: String,
    val port: Int,
    val userName: String,
    val password: String,
    val protocol: String
)
