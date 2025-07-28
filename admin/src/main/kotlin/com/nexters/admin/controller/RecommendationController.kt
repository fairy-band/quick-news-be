package com.nexters.admin.controller

import com.nexters.external.entity.Category
import com.nexters.external.entity.Content
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.resolver.DayContentResolver
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/recommendations")
class RecommendationController(
    private val categoryRepository: CategoryRepository,
    private val dayContentResolver: DayContentResolver
) {
    @GetMapping("/categories")
    fun getAllCategories(): ResponseEntity<List<Category>> = ResponseEntity.ok(categoryRepository.findAll())

    @GetMapping("/categories/{categoryId}/contents")
    fun getTodayRecommendedContents(
        @PathVariable categoryId: Long
    ): ResponseEntity<List<Content>> {
        // 임시 userId 값 사용 (실제로는 인증된 사용자의 ID를 사용해야 함)
        val userId = 1L
        val contents = dayContentResolver.resolveTodayContents(userId, categoryId)
        return ResponseEntity.ok(contents)
    }
}
