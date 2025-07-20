package com.nexters.admin.controller

import com.nexters.external.entity.CandidateKeyword
import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.CategoryKeywordMappingRepository
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.service.KeywordService
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
    private val keywordService: KeywordService
) {
    @GetMapping("/candidate")
    fun getAllCandidateKeywords(): ResponseEntity<List<CandidateKeyword>> = ResponseEntity.ok(candidateKeywordRepository.findAll())

    @PostMapping("/candidate")
    fun createCandidateKeyword(
        @RequestBody request: CreateKeywordRequest
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
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(candidateKeywordRepository.save(newKeyword))
    }

    @GetMapping("/reserved")
    fun getAllReservedKeywords(): ResponseEntity<List<ReservedKeyword>> = ResponseEntity.ok(reservedKeywordRepository.findAll())

    @PostMapping("/reserved")
    fun createReservedKeyword(
        @RequestBody request: CreateKeywordRequest
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
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(reservedKeywordRepository.save(newKeyword))
    }

    @GetMapping("/categories")
    fun getAllCategories(): ResponseEntity<List<Category>> = ResponseEntity.ok(categoryRepository.findAll())

    @PostMapping("/categories")
    fun createCategory(
        @RequestBody request: CreateCategoryRequest
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
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(categoryRepository.save(newCategory))
    }

    @GetMapping("/categories/{categoryId}/keywords")
    fun getKeywordsByCategory(
        @PathVariable categoryId: Long
    ): ResponseEntity<List<ReservedKeyword>> = ResponseEntity.ok(reservedKeywordRepository.findReservedKeywordsByCategoryId(categoryId))

    @PostMapping("/candidate/{candidateId}/promote")
    fun promoteCandidateToReserved(
        @PathVariable candidateId: Long
    ): ResponseEntity<ReservedKeyword> {
        val reservedKeyword = keywordService.promoteCandidateKeyword(candidateId)
        return ResponseEntity.ok(reservedKeyword)
    }

    @PostMapping("/categories/{categoryId}/keywords")
    fun addKeywordToCategory(
        @PathVariable categoryId: Long,
        @RequestBody request: AddKeywordToCategoryRequest
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
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(categoryKeywordMappingRepository.save(mapping))
    }

    @PutMapping("/categories/{categoryId}/keywords/{keywordId}")
    fun updateKeywordWeight(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long,
        @RequestBody request: UpdateKeywordWeightRequest
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
                updatedAt = LocalDateTime.now()
            )

        return ResponseEntity.ok(categoryKeywordMappingRepository.save(updatedMapping))
    }

    @DeleteMapping("/categories/{categoryId}/keywords/{keywordId}")
    fun removeKeywordFromCategory(
        @PathVariable categoryId: Long,
        @PathVariable keywordId: Long
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
}

data class AddKeywordToCategoryRequest(
    val keywordId: Long,
    val weight: Double
)

data class UpdateKeywordWeightRequest(
    val weight: Double
)

data class CreateKeywordRequest(
    val name: String
)

data class CreateCategoryRequest(
    val name: String
)
