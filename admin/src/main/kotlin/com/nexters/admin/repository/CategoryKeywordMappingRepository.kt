package com.nexters.admin.repository

import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryKeywordMappingRepository : JpaRepository<CategoryKeywordMapping, Long> {
    fun findByCategoryAndKeyword(
        category: Category,
        keyword: ReservedKeyword
    ): CategoryKeywordMapping?

    fun findByCategory(category: Category): List<CategoryKeywordMapping>
    
    fun findByKeyword(keyword: ReservedKeyword): List<CategoryKeywordMapping>
}
