package com.nexters.newsletterfeeder.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["com.nexters.external.repository"])
@EnableConfigurationProperties(MailProperties::class)
class DatabaseConfig 