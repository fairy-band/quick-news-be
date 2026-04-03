package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ReservedKeywordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ContentKeywordService(
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val candidateKeywordRepository: CandidateKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
) {
    private val logger = LoggerFactory.getLogger(ContentKeywordService::class.java)

    fun getReservedKeywordNames(): List<String> =
        reservedKeywordRepository
            .findAll()
            .map { it.name }
            .toList()

    fun findReservedKeywordsByNames(names: List<String>): List<ReservedKeyword> = reservedKeywordRepository.findByNameIn(names)

    @Transactional
    fun assignKeywordsToContent(
        content: Content,
        matchedKeywords: List<ReservedKeyword>,
    ) {
        matchedKeywords.forEach { keyword ->
            val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
            if (existingMapping == null) {
                val mapping =
                    ContentKeywordMapping(
                        content = content,
                        keyword = keyword,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                    )
                contentKeywordMappingRepository.save(mapping)
                logger.debug("Assigned keyword '${keyword.name}' to content ID: ${content.id}")
            } else {
                logger.debug("Keyword '${keyword.name}' already assigned to content ID: ${content.id}")
            }
        }
    }

    @Transactional
    fun promoteCandidateKeyword(candidateKeywordId: Long): ReservedKeyword {
        val candidateKeyword =
            candidateKeywordRepository
                .findById(candidateKeywordId)
                .orElseThrow { NoSuchElementException("CandidateKeyword not found with id: $candidateKeywordId") }

        val reservedKeyword =
            reservedKeywordRepository.findByName(candidateKeyword.name)
                ?: ReservedKeyword(name = candidateKeyword.name).also {
                    reservedKeywordRepository.save(it)
                }

        candidateKeywordRepository.delete(candidateKeyword)

        return reservedKeyword
    }
}
