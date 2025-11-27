package com.nexters.admin.controller

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentProviderRepository
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.service.KeywordService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
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
import org.springframework.web.bind.annotation.RequestParam
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
    private val summaryRepository: SummaryRepository,
    private val exposureContentRepository: ExposureContentRepository,
    private val keywordService: KeywordService,
    private val contentProviderRepository: ContentProviderRepository
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
        val contentProvider = contentProviderRepository.findByName(request.newsletterName)

        val newContent =
            Content(
                newsletterSourceId = request.newsletterSourceId,
                title = request.title,
                content = request.content,
                newsletterName = request.newsletterName,
                originalUrl = request.originalUrl,
                publishedAt = request.publishedAt,
                contentProvider = contentProvider,
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

        val updatedNewsletterName = request.newsletterName ?: existingContent.newsletterName
        val contentProvider =
            if (request.newsletterName != null) {
                contentProviderRepository.findByName(updatedNewsletterName)
            } else {
                existingContent.contentProvider
            }

        val updatedContent =
            Content(
                id = existingContent.id,
                newsletterSourceId = request.newsletterSourceId ?: existingContent.newsletterSourceId,
                title = request.title ?: existingContent.title,
                content = request.content ?: existingContent.content,
                newsletterName = updatedNewsletterName,
                originalUrl = request.originalUrl ?: existingContent.originalUrl,
                publishedAt = request.publishedAt ?: existingContent.publishedAt,
                contentProvider = contentProvider,
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

    @GetMapping("/sorted")
    fun getSortedContents(
        @RequestParam("sortOption") sortOption: String,
        @RequestParam("newsletterName", required = false) newsletterName: String?,
        pageable: Pageable
    ): ResponseEntity<Page<ContentWithSortInfoResponse>> {
        // 정렬 옵션에 따라 적절한 레포지토리 메서드 사용
        val contents =
            when (sortOption) {
                "noSummary" -> {
                    // 요약 없는 콘텐츠 우선 정렬 (DB 쿼리로 처리)
                    if (newsletterName != null) {
                        contentRepository.findContentsWithoutSummaryByNewsletterName(newsletterName, pageable)
                    } else {
                        contentRepository.findContentsWithoutSummary(pageable)
                    }
                }
                "notExposed" -> {
                    // 노출 안 하는 콘텐츠 우선 정렬 (DB 쿼리로 처리)
                    if (newsletterName != null) {
                        exposureContentRepository.findContentsWithoutExposureByNewsletterName(newsletterName, pageable)
                    } else {
                        exposureContentRepository.findContentsWithoutExposure(pageable)
                    }
                }
                "exposed" -> {
                    // 노출 중인 콘텐츠 우선 정렬 (DB 쿼리로 처리)
                    if (newsletterName != null) {
                        exposureContentRepository.findContentsWithExposureByNewsletterName(newsletterName, pageable)
                    } else {
                        exposureContentRepository.findContentsWithExposure(pageable)
                    }
                }
                else -> {
                    // 발행일 기준 정렬 (기본값)
                    val pageRequest =
                        PageRequest.of(
                            pageable.pageNumber,
                            pageable.pageSize,
                            Sort.by(Sort.Direction.DESC, "publishedAt")
                        )
                    if (newsletterName != null) {
                        contentRepository.findByNewsletterName(newsletterName, pageRequest)
                    } else {
                        contentRepository.findAll(pageRequest)
                    }
                }
            }

        // 요약 정보 및 노출 정보 가져오기
        val contentResponses =
            contents.map { content ->
                val hasSummary = summaryRepository.findByContent(content).isNotEmpty()
                val isExposed = exposureContentRepository.findByContent(content) != null

                ContentWithSortInfoResponse(
                    id = content.id ?: 0,
                    newsletterSourceId = content.newsletterSourceId,
                    title = content.title,
                    content = content.content,
                    newsletterName = content.newsletterName,
                    originalUrl = content.originalUrl,
                    publishedAt = content.publishedAt,
                    createdAt = content.createdAt,
                    updatedAt = content.updatedAt,
                    hasSummary = hasSummary,
                    isExposed = isExposed
                )
            }

        return ResponseEntity.ok(contentResponses)
    }

    @GetMapping("/by-category/{categoryId}/sorted")
    fun getSortedContentsByCategory(
        @PathVariable categoryId: Long,
        @RequestParam("sortOption") sortOption: String,
        @RequestParam("newsletterName", required = false) newsletterName: String?,
        pageable: Pageable
    ): ResponseEntity<Page<ContentWithSortInfoResponse>> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        // 정렬 옵션에 따라 적절한 레포지토리 메서드 사용
        val contents =
            when (sortOption) {
                "noSummary" -> {
                    // 요약 없는 콘텐츠 우선 정렬 (DB 쿼리로 처리)
                    if (newsletterName != null) {
                        contentRepository.findContentsByCategoryWithoutSummaryAndNewsletterName(categoryId, newsletterName, pageable)
                    } else {
                        contentRepository.findContentsByCategoryWithoutSummary(categoryId, pageable)
                    }
                }
                "notExposed" -> {
                    // 노출 안 하는 콘텐츠 우선 정렬 (DB 쿼리로 처리)
                    if (newsletterName != null) {
                        exposureContentRepository.findContentsByCategoryWithoutExposureAndNewsletterName(
                            categoryId,
                            newsletterName,
                            pageable
                        )
                    } else {
                        exposureContentRepository.findContentsByCategoryWithoutExposure(categoryId, pageable)
                    }
                }
                "exposed" -> {
                    // 노출 중인 콘텐츠 우선 정렬 (DB 쿼리로 처리)
                    if (newsletterName != null) {
                        exposureContentRepository.findContentsByCategoryWithExposureAndNewsletterName(categoryId, newsletterName, pageable)
                    } else {
                        exposureContentRepository.findContentsByCategoryWithExposure(categoryId, pageable)
                    }
                }
                else -> {
                    // 발행일 기준 정렬 (기본값)
                    val pageRequest =
                        PageRequest.of(
                            pageable.pageNumber,
                            pageable.pageSize,
                            Sort.by(Sort.Direction.DESC, "publishedAt")
                        )
                    if (newsletterName != null) {
                        contentRepository.findContentsByCategoryAndNewsletterName(categoryId, newsletterName, pageRequest)
                    } else {
                        contentRepository.findContentsByCategory(categoryId, pageRequest)
                    }
                }
            }

        // 요약 정보 및 노출 정보 가져오기
        val contentResponses =
            contents.map { content ->
                val hasSummary = summaryRepository.findByContent(content).isNotEmpty()
                val isExposed = exposureContentRepository.findByContent(content) != null

                ContentWithSortInfoResponse(
                    id = content.id ?: 0,
                    newsletterSourceId = content.newsletterSourceId,
                    title = content.title,
                    content = content.content,
                    newsletterName = content.newsletterName,
                    originalUrl = content.originalUrl,
                    publishedAt = content.publishedAt,
                    createdAt = content.createdAt,
                    updatedAt = content.updatedAt,
                    hasSummary = hasSummary,
                    isExposed = isExposed
                )
            }

        return ResponseEntity.ok(contentResponses)
    }

    @GetMapping("/by-keyword/{keywordId}")
    fun getContentsByKeyword(
        @PathVariable keywordId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<ContentWithSortInfoResponse>> {
        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("Keyword not found with id: $keywordId") }

        val contents = contentRepository.findContentsByKeywordId(keywordId, pageable)

        // 요약 정보 및 노출 정보 가져오기
        val contentResponses =
            contents.map { content ->
                val hasSummary = summaryRepository.findByContent(content).isNotEmpty()
                val isExposed = exposureContentRepository.findByContent(content) != null

                ContentWithSortInfoResponse(
                    id = content.id ?: 0,
                    newsletterSourceId = content.newsletterSourceId,
                    title = content.title,
                    content = content.content,
                    newsletterName = content.newsletterName,
                    originalUrl = content.originalUrl,
                    publishedAt = content.publishedAt,
                    createdAt = content.createdAt,
                    updatedAt = content.updatedAt,
                    hasSummary = hasSummary,
                    isExposed = isExposed
                )
            }

        return ResponseEntity.ok(contentResponses)
    }

    @PostMapping("/{contentId}/keywords/match-reserved")
    fun matchReservedKeywords(
        @PathVariable contentId: Long
    ): ResponseEntity<MatchReservedKeywordsResponse> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        // Use KeywordService to match reserved keywords
        val matchedKeywords = keywordService.matchReservedKeywords(content.content)

        // Get all reserved keywords for total count and suggestions
        val allReservedKeywords = reservedKeywordRepository.findAll()
        val reservedKeywordNames = allReservedKeywords.map { it.name }

        // Get full keyword result for suggestions and provocative keywords
        val keywordResult = keywordService.extractKeywords(reservedKeywordNames, content.content)

        // Process matched keywords and add them to content
        val addedKeywords = mutableListOf<String>()
        val alreadyExistingKeywords = mutableListOf<String>()

        matchedKeywords.forEach { keyword ->
            // Check if mapping already exists
            val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
            if (existingMapping == null) {
                // Add keyword to content
                val mapping =
                    ContentKeywordMapping(
                        content = content,
                        keyword = keyword,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                contentKeywordMappingRepository.save(mapping)
                addedKeywords.add(keyword.name)
            } else {
                alreadyExistingKeywords.add(keyword.name)
            }
        }

        return ResponseEntity.ok(
            MatchReservedKeywordsResponse(
                success = true,
                totalReservedKeywords = allReservedKeywords.size,
                matchedKeywords = matchedKeywords.map { it.name },
                addedKeywords = addedKeywords,
                alreadyExistingKeywords = alreadyExistingKeywords,
                suggestedKeywords = keywordResult.suggestedKeywords,
                provocativeKeywords = keywordResult.provocativeKeywords,
                message =
                    "총 ${allReservedKeywords.size}개의 예약 키워드 중 ${matchedKeywords.size}개가 매칭되었습니다. " +
                        "${addedKeywords.size}개가 새로 추가되었고, ${alreadyExistingKeywords.size}개는 이미 존재합니다."
            )
        )
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

data class ContentWithSortInfoResponse(
    val id: Long,
    val newsletterSourceId: String?,
    val title: String,
    val content: String,
    val newsletterName: String,
    val originalUrl: String,
    val publishedAt: LocalDate,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val hasSummary: Boolean,
    val isExposed: Boolean = false
)

data class MatchReservedKeywordsResponse(
    val success: Boolean,
    val totalReservedKeywords: Int,
    val matchedKeywords: List<String>,
    val addedKeywords: List<String>,
    val alreadyExistingKeywords: List<String>,
    val suggestedKeywords: List<String>,
    val provocativeKeywords: List<String>,
    val message: String
)
