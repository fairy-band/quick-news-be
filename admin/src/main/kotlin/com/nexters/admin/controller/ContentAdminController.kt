package com.nexters.admin.controller

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@Controller
@RequestMapping("/admin/contents")
class ContentAdminController(
    private val categoryRepository: CategoryRepository,
) {
    @GetMapping
    fun getContentsPage(model: Model): String {
        model.addAttribute("categories", categoryRepository.findAll())
        return "contents"
    }
}

@RestController
@RequestMapping("/api/contents")
class ContentApiController(
    private val contentRepository: ContentRepository,
    private val categoryRepository: CategoryRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val summaryRepository: SummaryRepository
) {
    @GetMapping
    fun getAllContents(pageable: Pageable): ResponseEntity<Page<Content>> = ResponseEntity.ok(contentRepository.findAll(pageable))

    @GetMapping("/newsletter-names")
    fun getNewsletterNames(): ResponseEntity<List<String>> {
        val newsletterNames = contentRepository.findDistinctNewsletterNames()
        return ResponseEntity.ok(newsletterNames)
    }

    @PostMapping
    fun createContent(
        @RequestBody request: CreateContentRequest
    ): ResponseEntity<Content> {
        val newContent =
            Content(
                newsletterSourceId = request.newsletterSourceId,
                title = request.title,
                content = request.content,
                newsletterName = request.newsletterName,
                originalUrl = request.originalUrl,
                publishedAt = request.publishedAt,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedContent = contentRepository.save(newContent)
        return ResponseEntity.ok(savedContent)
    }

    @GetMapping("/{contentId}")
    fun getContentById(
        @PathVariable contentId: Long
    ): ResponseEntity<Content> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }
        return ResponseEntity.ok(content)
    }

    @PutMapping("/{contentId}")
    fun updateContent(
        @PathVariable contentId: Long,
        @RequestBody request: UpdateContentRequest
    ): ResponseEntity<Content> {
        val existingContent =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val updatedContent =
            Content(
                id = existingContent.id,
                newsletterSourceId = request.newsletterSourceId ?: existingContent.newsletterSourceId,
                title = request.title ?: existingContent.title,
                content = request.content ?: existingContent.content,
                newsletterName = request.newsletterName ?: existingContent.newsletterName,
                originalUrl = request.originalUrl ?: existingContent.originalUrl,
                publishedAt = request.publishedAt ?: existingContent.publishedAt,
                createdAt = existingContent.createdAt,
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(contentRepository.save(updatedContent))
    }

    @DeleteMapping("/{contentId}")
    fun deleteContent(
        @PathVariable contentId: Long
    ): ResponseEntity<Void> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        // Delete all keyword mappings first
        val mappings = contentKeywordMappingRepository.findByContent(content)
        contentKeywordMappingRepository.deleteAll(mappings)

        // Then delete the content
        contentRepository.delete(content)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{contentId}/keywords")
    fun getContentKeywords(
        @PathVariable contentId: Long
    ): ResponseEntity<List<ReservedKeyword>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val mappings = contentKeywordMappingRepository.findByContent(content)
        val keywords = mappings.map { it.keyword }

        return ResponseEntity.ok(keywords)
    }

    @PostMapping("/{contentId}/keywords")
    fun addKeywordToContent(
        @PathVariable contentId: Long,
        @RequestBody request: AddKeywordToContentRequest
    ): ResponseEntity<ContentKeywordMapping> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val keyword =
            reservedKeywordRepository
                .findById(request.keywordId)
                .orElseThrow { NoSuchElementException("Keyword not found with id: ${request.keywordId}") }

        // Check if mapping already exists
        val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
        if (existingMapping != null) {
            throw IllegalArgumentException("This keyword is already mapped to the content")
        }

        val mapping =
            ContentKeywordMapping(
                content = content,
                keyword = keyword,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(contentKeywordMappingRepository.save(mapping))
    }

    @DeleteMapping("/{contentId}/keywords/{keywordId}")
    fun removeKeywordFromContent(
        @PathVariable contentId: Long,
        @PathVariable keywordId: Long
    ): ResponseEntity<Void> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("Keyword not found with id: $keywordId") }

        val mapping =
            contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
                ?: throw NoSuchElementException("Mapping not found for content $contentId and keyword $keywordId")

        contentKeywordMappingRepository.delete(mapping)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/by-category/{categoryId}")
    fun getContentsByCategory(
        @PathVariable categoryId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<ContentWithSummaryStatusResponse>> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val contents = contentRepository.findContentsByCategory(categoryId, pageable)

        val contentResponses =
            contents.map { content ->
                val hasSummary = summaryRepository.findByContent(content).isNotEmpty()
                ContentWithSummaryStatusResponse(
                    id = content.id ?: 0,
                    newsletterSourceId = content.newsletterSourceId,
                    title = content.title,
                    content = content.content,
                    newsletterName = content.newsletterName,
                    originalUrl = content.originalUrl,
                    publishedAt = content.publishedAt,
                    createdAt = content.createdAt,
                    updatedAt = content.updatedAt,
                    hasSummary = hasSummary
                )
            }

        return ResponseEntity.ok(contentResponses)
    }

    @GetMapping("/{contentId}/has-summary")
    fun checkContentHasSummary(
        @PathVariable contentId: Long
    ): ResponseEntity<Map<String, Any>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val hasSummary = summaryRepository.findByContent(content).isNotEmpty()

        return ResponseEntity.ok(mapOf("hasSummary" to hasSummary))
    }

    @GetMapping("/with-summary-status")
    fun getAllContentsWithSummaryStatus(pageable: Pageable): ResponseEntity<Page<ContentWithSummaryStatusResponse>> {
        val contents = contentRepository.findAll(pageable)

        val contentResponses =
            contents.map { content ->
                val hasSummary = summaryRepository.findByContent(content).isNotEmpty()
                ContentWithSummaryStatusResponse(
                    id = content.id ?: 0,
                    newsletterSourceId = content.newsletterSourceId,
                    title = content.title,
                    content = content.content,
                    newsletterName = content.newsletterName,
                    originalUrl = content.originalUrl,
                    publishedAt = content.publishedAt,
                    createdAt = content.createdAt,
                    updatedAt = content.updatedAt,
                    hasSummary = hasSummary
                )
            }

        return ResponseEntity.ok(contentResponses)
    }
}

data class AddKeywordToContentRequest(
    val keywordId: Long
)

data class CreateContentRequest(
    val newsletterSourceId: String? = null,
    val title: String,
    val content: String,
    val newsletterName: String,
    val originalUrl: String,
    val publishedAt: LocalDate
)

data class UpdateContentRequest(
    val newsletterSourceId: String? = null,
    val title: String? = null,
    val content: String? = null,
    val newsletterName: String? = null,
    val originalUrl: String? = null,
    val publishedAt: LocalDate? = null
)

data class ContentWithSummaryStatusResponse(
    val id: Long,
    val newsletterSourceId: String?,
    val title: String,
    val content: String,
    val newsletterName: String,
    val originalUrl: String,
    val publishedAt: LocalDate,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val hasSummary: Boolean
)
