package com.nexters.external.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "web_page_crawl_cache")
data class WebPageCrawlCache(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val url: String,
    val title: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val length: Int = 0,
    val success: Boolean = true,
    @Indexed(name = "ttl_idx", expireAfter = "7d")
    @CreatedDate
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
