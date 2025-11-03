package com.nexters.external.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "rss")
data class RssFeedProperties(
    var feeds: List<String> = emptyList()
)
