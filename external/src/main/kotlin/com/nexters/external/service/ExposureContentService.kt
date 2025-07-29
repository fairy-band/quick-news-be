package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.Summary
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.SummaryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ExposureContentService(
    private val exposureContentRepository: ExposureContentRepository,
    private val summaryRepository: SummaryRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
) {
    private val logger = LoggerFactory.getLogger(ExposureContentService::class.java)

    @Transactional
    fun createExposureContentFromSummary(summaryId: Long): ExposureContent {
        val summary =
            summaryRepository
                .findById(summaryId)
                .orElseThrow { NoSuchElementException("Summary not found with ID: $summaryId") }

        // Check if exposure content already exists for this content
        val existingExposureContent = exposureContentRepository.findByContent(summary.content)

        if (existingExposureContent != null) {
            logger.info("Exposure content already exists for content ID: ${summary.content.id}")
            return existingExposureContent
        }

        // Get the most provocative keyword for this content
        val provocativeKeyword = findMostProvocativeKeyword(summary.content)

        // Get the most provocative headline from the summary
        val provocativeHeadline = findProvocativeHeadline(summary)

        // Create and save the exposure content
        val exposureContent =
            ExposureContent(
                content = summary.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summary.summarizedContent,
            )

        return exposureContentRepository.save(exposureContent)
    }

    @Transactional
    fun setActiveSummaryAsExposureContent(summaryId: Long): ExposureContent {
        val summary =
            summaryRepository
                .findById(summaryId)
                .orElseThrow { NoSuchElementException("Summary not found with ID: $summaryId") }

        // Delete any existing exposure content for this content
        exposureContentRepository
            .findByContent(summary.content)
            ?.let {
                exposureContentRepository.deleteById(it.id!!)
                logger.info("Deleted existing exposure content ID: ${it.id} for content ID: $it.id")
            }

        // Get the most provocative keyword for this content
        val provocativeKeyword = findMostProvocativeKeyword(summary.content)

        // Create and save the new exposure content
        val exposureContent =
            ExposureContent(
                content = summary.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = summary.title,
                summaryContent = summary.summarizedContent,
            )

        return exposureContentRepository.save(exposureContent)
    }

    private fun findMostProvocativeKeyword(content: Content): String {
        val keywordMappings = contentKeywordMappingRepository.findByContent(content)

        return if (keywordMappings.isNotEmpty()) {
            // Return the first keyword as the most provocative one
            // In a real implementation, you might want to implement a more sophisticated algorithm
            keywordMappings.first().keyword.name
        } else {
            "No Keywords"
        }
    }

    private fun findProvocativeHeadline(summary: Summary): String {
        // In a real implementation, this might involve NLP or other analysis
        // For now, we'll just use the original title as the provocative headline
        return summary.title
    }

    fun getAllExposureContents() = exposureContentRepository.findAll()

    fun getExposureContentById(id: Long): ExposureContent =
        exposureContentRepository
            .findById(id)
            .orElseThrow { NoSuchElementException("Exposure content not found with ID: $id") }

    fun getExposureContentByContent(content: Content): ExposureContent? = exposureContentRepository.findByContent(content)

    @Transactional
    fun updateExposureContent(
        id: Long,
        provocativeKeyword: String,
        provocativeHeadline: String,
        summaryContent: String,
    ): ExposureContent {
        val existingContent = getExposureContentById(id)

        // Create a new instance with updated values
        // Since ExposureContent is immutable, we need to create a new instance
        val updatedContent =
            ExposureContent(
                id = existingContent.id,
                content = existingContent.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summaryContent,
                createdAt = existingContent.createdAt,
                updatedAt = LocalDateTime.now(),
            )

        return exposureContentRepository.save(updatedContent)
    }

    @Transactional
    fun deleteExposureContent(id: Long) {
        if (exposureContentRepository.existsById(id)) {
            exposureContentRepository.deleteById(id)
        } else {
            throw NoSuchElementException("Exposure content not found with ID: $id")
        }
    }

    @Transactional
    fun createOrUpdateExposureContent(
        content: Content,
        summary: Summary?,
        provocativeKeyword: String,
        provocativeHeadline: String,
        summaryContent: String
    ): ExposureContent {
        // Check if exposure content already exists for this content
        val existingExposureContent = exposureContentRepository.findByContent(content)
        
        if (existingExposureContent != null) {
            // Update existing exposure content
            val updatedContent = ExposureContent(
                id = existingExposureContent.id,
                content = existingExposureContent.content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summaryContent,
                createdAt = existingExposureContent.createdAt,
                updatedAt = LocalDateTime.now()
            )
            
            logger.info("Updated existing exposure content for content ID: ${content.id}")
            return exposureContentRepository.save(updatedContent)
        } else {
            // Create new exposure content
            val newExposureContent = ExposureContent(
                content = content,
                provocativeKeyword = provocativeKeyword,
                provocativeHeadline = provocativeHeadline,
                summaryContent = summaryContent,
            )
            
            logger.info("Created new exposure content for content ID: ${content.id}")
            return exposureContentRepository.save(newExposureContent)
        }
    }
}
