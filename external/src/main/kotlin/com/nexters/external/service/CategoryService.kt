package com.nexters.external.service

import com.nexters.external.entity.Category
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CategoryRepository
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {
    fun getTodayKeywordsByCategoryId(categoryId: Long): List<ReservedKeyword> {
        val category =
            categoryRepository.findById(categoryId).orElseThrow {
                IllegalArgumentException("Category with id $categoryId not found")
            }

        // TODO: 가중치 이후 키워드 정제 로직 추가
        return categoryRepository.findTop6KeywordsByCategoryId(categoryId)
    }

    fun getKeywordsByCategoryIds(categoryIds: List<Long>): List<ReservedKeyword> = categoryRepository.findKeywordsByCategoryIds(categoryIds)

    fun getKeywordWeightsByCategoryIds(categoryIds: List<Long>): Map<ReservedKeyword, Double> =
        categoryRepository.findCategoryKeywordMappingByCategoryIds(categoryIds).let {
            return it.associate { mapping ->
                mapping.keyword to mapping.weight
            }
        }

    fun getCategoryById(categoryId: Long): Category =
        categoryRepository.findById(categoryId).orElseThrow {
            IllegalArgumentException("Category with id $categoryId not found")
        }

    fun getAllCategories(): List<Category> = categoryRepository.findAll()
}
