package com.nexters.external.service

import com.nexters.external.entity.User
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
) {
    fun register(deviceToken: String): Long {
        val entity = User(deviceToken = deviceToken)
        val user = userRepository.save(entity)
        return user.id ?: throw IllegalStateException("User ID should not be null after saving.")
    }

    @Transactional
    fun updatePreferences(
        userId: Long,
        categoryName: String,
        keywords: List<String>
    ) {
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalArgumentException("User with ID $userId not found.")
            }

        val category = categoryRepository.findByName(categoryName) ?: throw IllegalArgumentException("Category $categoryName not found.")
        val keywords =
            reservedKeywordRepository.findByNameIn(keywords).ifEmpty {
                throw IllegalArgumentException("No valid keywords found for the provided names: $keywords")
            }

        user.categories.add(category)
        user.keywords.addAll(keywords)
        userRepository.save(user)
    }
}
