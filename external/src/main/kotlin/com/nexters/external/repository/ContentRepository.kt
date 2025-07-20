package com.nexters.external.repository

import com.nexters.external.entity.Content
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContentRepository : JpaRepository<Content, Long> {
    // NewsletterSource ID로 Content 조회
    fun findByMongoId(newsletterSourceId: String): List<Content>
    
    // 뉴스레터 이름으로 Content 조회
    fun findByNewsletterName(newsletterName: String): List<Content>
    
    // 제목에 키워드가 포함된 Content 조회
    fun findByTitleContaining(keyword: String): List<Content>
    
    // 특정 NewsletterSource에서 추출된 Content 개수 조회
    fun countByMongoId(newsletterSourceId: String): Long
} 