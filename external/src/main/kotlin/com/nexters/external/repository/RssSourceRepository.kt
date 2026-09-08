package com.nexters.external.repository

import com.nexters.external.entity.RssSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RssSourceRepository : JpaRepository<RssSource, Long> {
    fun findByIsActiveTrueOrderByPriorityDesc(): List<RssSource>
    fun findByFeedUrl(feedUrl: String): RssSource?
}
