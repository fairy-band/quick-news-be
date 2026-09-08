package com.nexters.external.repository

import com.nexters.external.entity.RssFeed
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RssFeedRepository : JpaRepository<RssFeed, Long> {
    fun findByIsActiveTrueOrderByPriorityDesc(): List<RssFeed>
    fun findByFeedUrl(feedUrl: String): RssFeed?
}
