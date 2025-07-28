package com.nexters.external.service

import com.nexters.external.repository.ContentRepository
import org.springframework.stereotype.Service

@Service
class ContentService(
    private val contentRepository: ContentRepository
) {
    fun getContentsByReservedKeywordIds(reservedKeywordIds: List<Long>) = contentRepository.findByReservedKeywordIds(reservedKeywordIds)
}
