package com.nexters.admin.dto

import com.nexters.external.entity.Category
import java.time.LocalDateTime

data class CategoryDto(
    val id: Long?,
    val name: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(category: Category): CategoryDto =
            CategoryDto(
                id = category.id,
                name = category.name,
                createdAt = category.createdAt,
                updatedAt = category.updatedAt
            )
    }
}
