package com.nexters.admin.controller

import com.nexters.admin.repository.CategoryKeywordMappingRepository
import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.resolver.DayContentResolver
import com.nexters.external.service.ExposureContentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/recommendations")
class RecommendationController(
    private val categoryRepository: CategoryRepository,
    private val dayContentResolver: DayContentResolver,
    private val categoryKeywordMappingRepository: CategoryKeywordMappingRepository,
    private val summaryRepository: SummaryRepository,
    private val exposureContentService: ExposureContentService,
    private val exposureContentRepository: ExposureContentRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val contentRepository: ContentRepository,
) {
    @GetMapping("/categories")
    fun getAllCategories(): ResponseEntity<List<Category>> = ResponseEntity.ok(categoryRepository.findAll())

    @GetMapping("/categories/{categoryId}/contents")
    fun getTodayRecommendedContents(
        @PathVariable categoryId: Long
    ): ResponseEntity<List<RecommendedContentResponse>> {
        // 임시 userId 값 사용 (실제로는 인증된 사용자의 ID를 사용해야 함)
        val userId = 1L
        val contents = dayContentResolver.resolveTodayContents(userId, categoryId)

        val response =
            contents.map { content ->
                val exposureContent =
                    exposureContentRepository.findByContent(content)

                RecommendedContentResponse(
                    content =
                        ContentResponse(
                            id = content.id!!,
                            title = content.title,
                            content = content.content,
                            newsletterName = content.newsletterName,
                            originalUrl = content.originalUrl
                        ),
                    exposureContent =
                        exposureContent?.let {
                            ExposureContentResponse(
                                id = it.id!!,
                                contentId = it.content.id!!,
                                title = it.content.title,
                                provocativeKeyword = it.provocativeKeyword,
                                provocativeHeadline = it.provocativeHeadline,
                                summaryContent = it.summaryContent
                            )
                        }
                )
            }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/categories/{categoryId}/keywords")
    fun getCategoryKeywordsWithWeights(
        @PathVariable categoryId: Long
    ): ResponseEntity<List<CategoryKeywordResponse>> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val mappings = categoryKeywordMappingRepository.findByCategory(category)
        val response =
            mappings.map { mapping ->
                CategoryKeywordResponse(
                    id = mapping.keyword.id!!,
                    name = mapping.keyword.name,
                    weight = mapping.weight
                )
            }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/categories/{categoryId}/negative-keywords")
    fun getCategoryNegativeKeywords(
        @PathVariable categoryId: Long
    ): ResponseEntity<List<CategoryKeywordResponse>> {
        val negativeKeywords = dayContentResolver.getNegativeKeywords(categoryId)

        val response =
            negativeKeywords.map { keyword ->
                // 카테고리에 해당하는 키워드 가중치 맵 생성
                val categoryKeywordWeights =
                    categoryRepository
                        .findById(categoryId)
                        .map { category ->
                            val mapping = categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
                            CategoryKeywordResponse(
                                id = keyword.id!!,
                                name = keyword.name,
                                weight = mapping?.weight ?: 0.0
                            )
                        }.orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

                categoryKeywordWeights
            }

        return ResponseEntity.ok(response)
    }

    @PutMapping("/categories/{categoryId}/keywords/{keywordId}/weight")
    fun updateCategoryKeywordWeight(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long,
        @RequestBody request: UpdateKeywordWeightRequest
    ): ResponseEntity<CategoryKeywordResponse> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("Keyword not found with id: $keywordId") }

        val mapping =
            categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
                ?: throw NoSuchElementException("Mapping not found for category $categoryId and keyword $keywordId")

        // Create a new mapping with updated weight and timestamp
        val updatedMapping =
            CategoryKeywordMapping(
                id = mapping.id,
                category = mapping.category,
                keyword = mapping.keyword,
                weight = request.weight,
                createdAt = mapping.createdAt,
                updatedAt = LocalDateTime.now()
            )

        val savedMapping = categoryKeywordMappingRepository.save(updatedMapping)

        return ResponseEntity.ok(
            CategoryKeywordResponse(
                id = savedMapping.keyword.id!!,
                name = savedMapping.keyword.name,
                weight = savedMapping.weight
            )
        )
    }

    @GetMapping("/summaries")
    fun getAllSummaries(): ResponseEntity<List<RecommendSummaryResponse>> {
        val summaries = summaryRepository.findAll()
        val response =
            summaries.map { summary ->
                RecommendSummaryResponse(
                    id = summary.id!!,
                    title = summary.title,
                    contentId = summary.content.id!!,
                    newsletterName = summary.content.newsletterName,
                    summarizedAt = summary.summarizedAt.toString(),
                    hasExposureContent = exposureContentService.getExposureContentByContent(summary.content) != null
                )
            }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/summaries/{summaryId}/create-exposure")
    fun createExposureContent(
        @PathVariable summaryId: Long
    ): ResponseEntity<ExposureContentResponse> {
        val exposureContent = exposureContentService.createExposureContentFromSummary(summaryId)

        return ResponseEntity.ok(
            ExposureContentResponse(
                id = exposureContent.id!!,
                contentId = exposureContent.content.id!!,
                title = exposureContent.content.title,
                provocativeKeyword = exposureContent.provocativeKeyword,
                provocativeHeadline = exposureContent.provocativeHeadline,
                summaryContent = exposureContent.summaryContent
            )
        )
    }

    @PostMapping("/summaries/{summaryId}/set-active")
    fun setActiveSummary(
        @PathVariable summaryId: Long
    ): ResponseEntity<ExposureContentResponse> {
        val exposureContent = exposureContentService.setActiveSummaryAsExposureContent(summaryId)

        return ResponseEntity.ok(
            ExposureContentResponse(
                id = exposureContent.id!!,
                contentId = exposureContent.content.id!!,
                title = exposureContent.content.title,
                provocativeKeyword = exposureContent.provocativeKeyword,
                provocativeHeadline = exposureContent.provocativeHeadline,
                summaryContent = exposureContent.summaryContent
            )
        )
    }

    @GetMapping("/exposure-contents")
    fun getAllExposureContents(): ResponseEntity<List<ExposureContentResponse>> {
        val exposureContents = exposureContentService.getAllExposureContents()
        val response =
            exposureContents.map { exposureContent ->
                ExposureContentResponse(
                    id = exposureContent.id!!,
                    contentId = exposureContent.content.id!!,
                    title = exposureContent.content.title,
                    provocativeKeyword = exposureContent.provocativeKeyword,
                    provocativeHeadline = exposureContent.provocativeHeadline,
                    summaryContent = exposureContent.summaryContent
                )
            }
        return ResponseEntity.ok(response)
    }

    @GetMapping("/exposure-contents/{id}")
    fun getExposureContent(
        @PathVariable id: Long
    ): ResponseEntity<ExposureContentResponse> {
        val exposureContent = exposureContentService.getExposureContentById(id)

        return ResponseEntity.ok(
            ExposureContentResponse(
                id = exposureContent.id!!,
                contentId = exposureContent.content.id!!,
                title = exposureContent.content.title,
                provocativeKeyword = exposureContent.provocativeKeyword,
                provocativeHeadline = exposureContent.provocativeHeadline,
                summaryContent = exposureContent.summaryContent
            )
        )
    }

    @PutMapping("/exposure-contents/{id}")
    fun updateExposureContent(
        @PathVariable id: Long,
        @RequestBody request: UpdateExposureContentRequest
    ): ResponseEntity<ExposureContentResponse> {
        val updatedContent =
            exposureContentService.updateExposureContent(
                id = id,
                provocativeKeyword = request.provocativeKeyword,
                provocativeHeadline = request.provocativeHeadline,
                summaryContent = request.summaryContent
            )

        return ResponseEntity.ok(
            ExposureContentResponse(
                id = updatedContent.id!!,
                contentId = updatedContent.content.id!!,
                title = updatedContent.content.title,
                provocativeKeyword = updatedContent.provocativeKeyword,
                provocativeHeadline = updatedContent.provocativeHeadline,
                summaryContent = updatedContent.summaryContent
            )
        )
    }

    @DeleteMapping("/exposure-contents/{id}")
    fun deleteExposureContent(
        @PathVariable id: Long
    ): ResponseEntity<Map<String, Boolean>> {
        exposureContentService.deleteExposureContent(id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/categories/{categoryId}/all-keywords")
    fun getAllCategoryKeywords(
        @PathVariable categoryId: Long
    ): ResponseEntity<List<CategoryKeywordResponse>> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val mappings = categoryKeywordMappingRepository.findByCategory(category)
        val response =
            mappings.map { mapping ->
                CategoryKeywordResponse(
                    id = mapping.keyword.id!!,
                    name = mapping.keyword.name,
                    weight = mapping.weight
                )
            }

        return ResponseEntity.ok(response)
    }

    @PostMapping("/categories/{categoryId}/keywords")
    fun addKeywordToCategory(
        @PathVariable categoryId: Long,
        @RequestBody request: AddKeywordToCategoryRequest
    ): ResponseEntity<CategoryKeywordResponse> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(request.keywordId)
                .orElseThrow { NoSuchElementException("Keyword not found with id: ${request.keywordId}") }

        // Check if mapping already exists
        val existingMapping = categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
        if (existingMapping != null) {
            return ResponseEntity.ok(
                CategoryKeywordResponse(
                    id = existingMapping.keyword.id!!,
                    name = existingMapping.keyword.name,
                    weight = existingMapping.weight
                )
            )
        }

        val mapping =
            CategoryKeywordMapping(
                category = category,
                keyword = keyword,
                weight = request.weight,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedMapping = categoryKeywordMappingRepository.save(mapping)

        return ResponseEntity.ok(
            CategoryKeywordResponse(
                id = savedMapping.keyword.id!!,
                name = savedMapping.keyword.name,
                weight = savedMapping.weight
            )
        )
    }

    @DeleteMapping("/categories/{categoryId}/keywords/{keywordId}")
    fun removeKeywordFromCategory(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long
    ): ResponseEntity<Map<String, Boolean>> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("Keyword not found with id: $keywordId") }

        val mapping =
            categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
                ?: throw NoSuchElementException("Mapping not found for category $categoryId and keyword $keywordId")

        categoryKeywordMappingRepository.delete(mapping)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/exposure-contents")
    fun createExposureContentDirect(
        @RequestBody request: CreateExposureContentRequest
    ): ResponseEntity<ExposureContentResponse> {
        // Get the content
        val content =
            contentRepository
                .findById(request.contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: ${request.contentId}") }

        // Get the summary if provided
        val summary =
            request.summaryId?.let {
                summaryRepository
                    .findById(it)
                    .orElse(null)
            }

        // Create exposure content
        val exposureContent =
            exposureContentService.createOrUpdateExposureContent(
                content = content,
                summary = summary,
                provocativeKeyword = request.provocativeKeyword,
                provocativeHeadline = request.provocativeHeadline,
                summaryContent = request.summaryContent
            )

        return ResponseEntity.ok(
            ExposureContentResponse(
                id = exposureContent.id!!,
                contentId = exposureContent.content.id!!,
                title = exposureContent.content.title,
                provocativeKeyword = exposureContent.provocativeKeyword,
                provocativeHeadline = exposureContent.provocativeHeadline,
                summaryContent = exposureContent.summaryContent
            )
        )
    }
}

data class RecommendedContentResponse(
    val content: ContentResponse,
    val exposureContent: ExposureContentResponse?
)

data class ContentResponse(
    val id: Long,
    val title: String,
    val content: String,
    val newsletterName: String,
    val originalUrl: String
)

data class CategoryKeywordResponse(
    val id: Long,
    val name: String,
    val weight: Double
)

data class RecommendSummaryResponse(
    val id: Long,
    val title: String,
    val contentId: Long,
    val newsletterName: String,
    val summarizedAt: String,
    val hasExposureContent: Boolean
)

data class ExposureContentResponse(
    val id: Long,
    val contentId: Long,
    val title: String,
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String
)

data class UpdateExposureContentRequest(
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String
)

data class CreateExposureContentRequest(
    val contentId: Long,
    val summaryId: Long? = null,
    val provocativeKeyword: String,
    val provocativeHeadline: String,
    val summaryContent: String
)
