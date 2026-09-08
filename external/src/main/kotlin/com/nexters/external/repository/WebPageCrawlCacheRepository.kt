package com.nexters.external.repository

import com.nexters.external.entity.WebPageCrawlCache
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface WebPageCrawlCacheRepository : MongoRepository<WebPageCrawlCache, String> {
    fun findByUrl(url: String): WebPageCrawlCache?
}
