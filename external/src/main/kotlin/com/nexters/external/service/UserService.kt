package com.nexters.external.service

import com.nexters.external.entity.User
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

@Service
class UserService(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val notificationService: NotificationService,
    private val dailyContentArchiveService: DailyContentArchiveService,
) {
    private val userRegistrationLock = ReentrantLock() // TODO: 이후 upsert로 변경

    fun register(deviceToken: String): Long {
        userRegistrationLock.lock()
        try {
            // 먼저 기존 유저가 있는지 확인
            val existingUser = userRepository.findByDeviceToken(deviceToken)
            if (existingUser != null) {
                // 기존 유저가 있으면 해당 유저의 ID 반환
                return existingUser.id ?: throw IllegalStateException("Existing user ID should not be null.")
            }

            // 기존 유저가 없으면 새로 생성
            val entity = User(deviceToken = deviceToken)
            val user = userRepository.save(entity)
            val userId = user.id ?: throw IllegalStateException("User ID should not be null after saving.")

            notificationService.notifyUserRegistration(userId, deviceToken) // TODO: 이후 알림 많아지면 일반화 후 AOP로 분리

            return userId
        } finally {
            userRegistrationLock.unlock()
        }
    }

    fun findUser(deviceToken: String): User? = userRepository.findByDeviceToken(deviceToken)

    @Transactional
    fun updatePreferences(
        userId: Long,
        categoryNames: List<String>,
        keywords: List<String>
    ) {
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalArgumentException("User with ID $userId not found.")
            }

        val categories = categoryRepository.findByNameIn(categoryNames)
        val reservedKeywords =
            reservedKeywordRepository.findByNameIn(keywords).ifEmpty {
                throw IllegalArgumentException("No valid keywords found for the provided names: $keywords")
            }

        val oldCategoryIds = user.categories.mapNotNull { it.id }.toSet()
        val newCategoryIds = categories.mapNotNull { it.id }.toSet()

        val isCategoryChanging = oldCategoryIds.isNotEmpty() && oldCategoryIds != newCategoryIds

        if (isCategoryChanging) {
            if (user.categoryChangeCount >= MAX_ALLOWED_CATEGORY_CHANGES) {
                throw IllegalStateException("직군 변경은 계정당 최대 ${MAX_ALLOWED_CATEGORY_CHANGES}회만 가능합니다.")
            }
            user.categoryChangeCount += 1
            user.isCategoryChanged = true
        }

        user.categories = categories.toMutableSet()
        user.keywords = reservedKeywords.toMutableSet()
        userRepository.save(user)

        if (isCategoryChanging) {
            dailyContentArchiveService.deleteByDateAndUserId(userId, LocalDate.now())
        }
    }

    companion object {
        const val MAX_ALLOWED_CATEGORY_CHANGES = 1
    }

    fun getUserById(userId: Long): User =
        userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("User with ID $userId not found.")
        }

    @Transactional
    fun markOnboarded(userId: Long) {
        val user = getUserById(userId)
        user.isOnboarded = true
        userRepository.save(user)
    }
}
