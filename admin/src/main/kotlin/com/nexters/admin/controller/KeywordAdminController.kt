package com.nexters.admin.controller

import com.nexters.admin.dto.CategoryDto
import com.nexters.admin.repository.CategoryKeywordMappingRepository
import com.nexters.external.entity.CandidateKeyword
import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.service.ContentAnalysisService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
@RequestMapping("/api/keywords")
class KeywordAdminController(
    private val candidateKeywordRepository: CandidateKeywordRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryKeywordMappingRepository: CategoryKeywordMappingRepository,
    private val contentAnalysisService: ContentAnalysisService,
) {
    @GetMapping("/candidate")
    fun getAllCandidateKeywords(pageable: Pageable): ResponseEntity<Page<CandidateKeyword>> =
        ResponseEntity.ok(candidateKeywordRepository.findAll(pageable))

    @PostMapping("/candidate")
    fun createCandidateKeyword(
        @RequestBody request: CreateKeywordRequest,
    ): ResponseEntity<CandidateKeyword> {
        // Check if a candidate keyword with the same name already exists
        val existingCandidate = candidateKeywordRepository.findByName(request.name)
        if (existingCandidate != null) {
            return ResponseEntity.ok(existingCandidate)
        }

        // Check if a reserved keyword with the same name already exists
        val existingReserved = reservedKeywordRepository.findByName(request.name)
        if (existingReserved != null) {
            throw IllegalArgumentException("A reserved keyword with this name already exists")
        }

        val newKeyword =
            CandidateKeyword(
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        return ResponseEntity.ok(candidateKeywordRepository.save(newKeyword))
    }

    @DeleteMapping("/candidate/{candidateId}")
    fun deleteCandidateKeyword(
        @PathVariable candidateId: Long,
    ): ResponseEntity<Void> {
        val candidateKeyword =
            candidateKeywordRepository
                .findById(candidateId)
                .orElseThrow { NoSuchElementException("Candidate keyword not found with id: $candidateId") }

        candidateKeywordRepository.delete(candidateKeyword)

        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/candidate/batch")
    fun deleteCandidateKeywords(
        @RequestBody request: DeleteCandidateKeywordsRequest,
    ): ResponseEntity<DeleteCandidateKeywordsResponse> {
        val deletedIds = mutableListOf<Long>()
        val notFoundIds = mutableListOf<Long>()

        request.candidateIds.forEach { candidateId ->
            try {
                val candidateKeyword = candidateKeywordRepository.findById(candidateId)
                if (candidateKeyword.isPresent) {
                    candidateKeywordRepository.delete(candidateKeyword.get())
                    deletedIds.add(candidateId)
                } else {
                    notFoundIds.add(candidateId)
                }
            } catch (e: Exception) {
                notFoundIds.add(candidateId)
            }
        }

        return ResponseEntity.ok(
            DeleteCandidateKeywordsResponse(
                success = deletedIds.isNotEmpty(),
                deletedIds = deletedIds,
                notFoundIds = notFoundIds
            )
        )
    }

    @GetMapping("/reserved")
    fun getAllReservedKeywords(pageable: Pageable): ResponseEntity<Page<ReservedKeyword>> =
        ResponseEntity.ok(reservedKeywordRepository.findAll(pageable))

    @PostMapping("/reserved")
    fun createReservedKeyword(
        @RequestBody request: CreateKeywordRequest,
    ): ResponseEntity<ReservedKeyword> {
        // Check if keyword with same name already exists
        val existingKeyword = reservedKeywordRepository.findByName(request.name)
        if (existingKeyword != null) {
            return ResponseEntity.ok(existingKeyword)
        }

        val newKeyword =
            ReservedKeyword(
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        return ResponseEntity.ok(reservedKeywordRepository.save(newKeyword))
    }

    @GetMapping("/categories")
    fun getAllCategories(): ResponseEntity<List<CategoryDto>> = ResponseEntity.ok(categoryRepository.findAll().map { CategoryDto.from(it) })

    @PostMapping("/categories")
    fun createCategory(
        @RequestBody request: CreateCategoryRequest,
    ): ResponseEntity<Category> {
        // Check if category with same name already exists
        val existingCategory = categoryRepository.findByName(request.name)
        if (existingCategory != null) {
            return ResponseEntity.ok(existingCategory)
        }

        val newCategory =
            Category(
                name = request.name,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        return ResponseEntity.ok(categoryRepository.save(newCategory))
    }

    @GetMapping("/categories/{categoryId}/keywords")
    fun getKeywordsByCategory(
        @PathVariable categoryId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<ReservedKeyword>> =
        ResponseEntity.ok(reservedKeywordRepository.findReservedKeywordsByCategoryId(categoryId, pageable))

    @PostMapping("/candidate/{candidateId}/promote-to-reserved")
    fun promoteCandidateToReserved(
        @PathVariable candidateId: Long,
    ): ResponseEntity<ReservedKeyword> {
        val reservedKeyword = contentAnalysisService.promoteCandidateKeyword(candidateId)
        return ResponseEntity.ok(reservedKeyword)
    }

    @PostMapping("/candidate/{candidateId}/promote")
    fun promoteCandidateKeyword(
        @PathVariable candidateId: Long,
    ): ResponseEntity<ReservedKeyword> {
        val reservedKeyword = contentAnalysisService.promoteCandidateKeyword(candidateId)
        return ResponseEntity.ok(reservedKeyword)
    }

    @PostMapping("/categories/{categoryId}/keywords")
    fun addKeywordToCategory(
        @PathVariable categoryId: Long,
        @RequestBody request: AddKeywordToCategoryRequest,
    ): ResponseEntity<CategoryKeywordMapping> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(request.keywordId)
                .orElseThrow { NoSuchElementException("ReservedKeyword not found with id: ${request.keywordId}") }

        // Check if mapping already exists
        val existingMapping = categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
        if (existingMapping != null) {
            throw IllegalArgumentException("This keyword is already mapped to the category")
        }

        val mapping =
            CategoryKeywordMapping(
                category = category,
                keyword = keyword,
                weight = request.weight,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        return ResponseEntity.ok(categoryKeywordMappingRepository.save(mapping))
    }

    @PutMapping("/categories/{categoryId}/keywords/{keywordId}")
    fun updateKeywordWeight(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long,
        @RequestBody request: UpdateKeywordWeightRequest,
    ): ResponseEntity<CategoryKeywordMapping> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("ReservedKeyword not found with id: $keywordId") }

        val mapping =
            categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
                ?: throw NoSuchElementException("Mapping not found for category $categoryId and keyword $keywordId")

        val updatedMapping =
            CategoryKeywordMapping(
                id = mapping.id,
                category = mapping.category,
                keyword = mapping.keyword,
                weight = request.weight,
                createdAt = mapping.createdAt,
                updatedAt = LocalDateTime.now(),
            )

        return ResponseEntity.ok(categoryKeywordMappingRepository.save(updatedMapping))
    }

    @DeleteMapping("/categories/{categoryId}/keywords/{keywordId}")
    fun removeKeywordFromCategory(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long,
    ): ResponseEntity<Void> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("ReservedKeyword not found with id: $keywordId") }

        val mapping =
            categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
                ?: throw NoSuchElementException("Mapping not found for category $categoryId and keyword $keywordId")

        categoryKeywordMappingRepository.delete(mapping)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/categories/{categoryId}/keywords/{keywordId}/weight")
    fun getKeywordWeight(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long
    ): ResponseEntity<KeywordWeightResponse> {
        val category =
            categoryRepository
                .findById(categoryId)
                .orElseThrow { NoSuchElementException("Category not found with id: $categoryId") }

        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("ReservedKeyword not found with id: $keywordId") }

        val mapping =
            categoryKeywordMappingRepository.findByCategoryAndKeyword(category, keyword)
                ?: throw NoSuchElementException("Mapping not found for category $categoryId and keyword $keywordId")

        return ResponseEntity.ok(
            KeywordWeightResponse(
                categoryId = categoryId,
                keywordId = keywordId,
                weight = mapping.weight
            )
        )
    }

    @GetMapping("/reserved/{keywordId}/categories")
    fun getCategoriesByKeyword(
        @PathVariable keywordId: Long
    ): ResponseEntity<List<Category>> {
        val keyword =
            reservedKeywordRepository
                .findById(keywordId)
                .orElseThrow { NoSuchElementException("ReservedKeyword not found with id: $keywordId") }

        val mappings = categoryKeywordMappingRepository.findByKeyword(keyword)
        val categories = mappings.map { it.category }

        return ResponseEntity.ok(categories)
    }
}

data class AddKeywordToCategoryRequest(
    val keywordId: Long,
    val weight: Double,
)

data class UpdateKeywordWeightRequest(
    val weight: Double,
)

data class CreateKeywordRequest(
    val name: String,
)

data class CreateCategoryRequest(
    val name: String,
)

data class DeleteCandidateKeywordsRequest(
    val candidateIds: List<Long>,
)

data class DeleteCandidateKeywordsResponse(
    val success: Boolean,
    val deletedIds: List<Long> = emptyList(),
    val notFoundIds: List<Long> = emptyList(),
)

data class KeywordWeightResponse(
    val categoryId: Long,
    val keywordId: Long,
    val weight: Double
)
