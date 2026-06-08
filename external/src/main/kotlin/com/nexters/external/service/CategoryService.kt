package com.nexters.external.service

import com.nexters.external.entity.Category
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.CategoryRepository
import org.springframework.stereotype.Service

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {
    fun getKeywordsByCategoryIds(categoryIds: List<Long>): List<ReservedKeyword> = categoryRepository.findKeywordsByCategoryIds(categoryIds)

    fun getKeywordWeightsByCategoryIds(categoryIds: List<Long>): Map<ReservedKeyword, Double> =
        categoryRepository.findCategoryKeywordMappingByCategoryIds(categoryIds).let {
            return it.associate { mapping ->
                mapping.keyword to mapping.weight
            }
        }

    fun getContentProvidersByCategoryIds(categoryIds: List<Long>): List<ContentProvider> =
        categoryRepository.findContentProvidersByCategoryIds(categoryIds)

    fun getAllCategories(): List<Category> = categoryRepository.findAll()
}
