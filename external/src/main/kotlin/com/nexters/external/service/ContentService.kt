package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.repository.ContentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ContentService(
    private val contentRepository: ContentRepository
) {
    private val logger = LoggerFactory.getLogger(ContentService::class.java)

    /**
     * 단일 Content 저장
     */
    fun save(content: Content): Content {
        logger.info("Saving content: ${content.title} (NewsletterSource ID: ${content.mongoId})")
        return contentRepository.save(content)
    }

    /**
     * 여러 Content 일괄 저장
     */
    fun saveAll(contents: List<Content>): List<Content> {
        logger.info("Saving ${contents.size} contents")
        return contentRepository.saveAll(contents)
    }

    /**
     * NewsletterSource ID로 Content 목록 조회
     */
    fun findByNewsletterSourceId(newsletterSourceId: String): List<Content> {
        logger.info("Finding contents by NewsletterSource ID: $newsletterSourceId")
        return contentRepository.findByMongoId(newsletterSourceId)
    }

    /**
     * 뉴스레터 이름으로 Content 목록 조회
     */
    fun findByNewsletterName(newsletterName: String): List<Content> {
        logger.info("Finding contents by newsletter name: $newsletterName")
        return contentRepository.findByNewsletterName(newsletterName)
    }

    /**
     * 제목에 키워드가 포함된 Content 목록 조회
     */
    fun findByTitleContaining(keyword: String): List<Content> {
        logger.info("Finding contents by title keyword: $keyword")
        return contentRepository.findByTitleContaining(keyword)
    }

    /**
     * 특정 NewsletterSource에서 추출된 Content 개수 조회
     */
    fun countByNewsletterSourceId(newsletterSourceId: String): Long {
        return contentRepository.countByMongoId(newsletterSourceId)
    }

    /**
     * Content ID로 조회
     */
    fun findById(id: Long): Content? = contentRepository.findById(id).orElse(null)

    /**
     * Content 삭제
     */
    fun deleteById(id: Long) {
        contentRepository.deleteById(id)
    }

    /**
     * 전체 Content 개수 조회
     */
    fun count(): Long = contentRepository.count()
}
