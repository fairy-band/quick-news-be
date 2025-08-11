package com.nexters.external.service

import com.nexters.external.repository.ContentRepository
import org.springframework.stereotype.Service

@Service
class ContentService(
    private val contentRepository: ContentRepository
) {
    fun getContentsByReservedKeywordIds(reservedKeywordIds: List<Long>) = contentRepository.findByReservedKeywordIds(reservedKeywordIds)

    fun getNotExposedContentsByReservedKeywordIds(
        userId: Long,
        reservedKeywordIds: List<Long>
    ) = contentRepository.findNotExposedContents(userId, reservedKeywordIds)

    fun findContentsByKeywords(keywords: List<String>) =
        contentRepository.findAll().filter { content ->
            keywords.any { keyword ->
                content.title.contains(keyword, ignoreCase = true) ||
                    content.content.contains(keyword, ignoreCase = true)
            }
        }
}
