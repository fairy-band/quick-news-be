package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.repository.ContentRepository
import org.springframework.stereotype.Service

@Service
class ContentService(
    private val contentRepository: ContentRepository
) {
    fun save(content: Content): Content = contentRepository.save(content)
}
