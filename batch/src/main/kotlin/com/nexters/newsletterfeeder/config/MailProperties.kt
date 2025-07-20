package com.nexters.newsletterfeeder.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.mail")
data class MailProperties(
    var host: String = "", // val -> var로 변경
    var port: Int = 0,     // val -> var로 변경
    var username: String = "", // val -> var로 변경
    var password: String = "", // val -> var로 변경
    var protocol: String = ""  // val -> var로 변경
)
