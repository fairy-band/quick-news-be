package com.nexters.external.repository

import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByName(name: String): Category?

    @Query(
        """
        SELECT rk
        FROM ReservedKeyword rk
        JOIN CategoryKeywordMapping ckm ON rk.id = ckm.keyword.id
        WHERE ckm.category.id = :categoryId
        ORDER BY ckm.weight DESC
        LIMIT 6
    """
    )
    fun findTop6KeywordsByCategoryId(categoryId: Long): List<ReservedKeyword>

    @Query(
        """
        SELECT rk
        FROM ReservedKeyword rk
        JOIN CategoryKeywordMapping ckm ON rk.id = ckm.keyword.id
        WHERE ckm.category.id = :categoryId
        ORDER BY ckm.weight DESC
    """
    )
    fun findKeywordsByCategoryId(categoryId: Long): List<ReservedKeyword>

    @Query(
        """
        SELECT ckm
        FROM CategoryKeywordMapping ckm
        WHERE ckm.category.id = :categoryId
    """
    )
    fun findCategoryKeywordMappingByCategoryId(categoryId: Long): List<CategoryKeywordMapping>
}
