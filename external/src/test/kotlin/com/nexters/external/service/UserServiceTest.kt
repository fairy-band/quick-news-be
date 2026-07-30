package com.nexters.external.service

import com.nexters.external.entity.Category
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.User
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional

class UserServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val reservedKeywordRepository = mockk<ReservedKeywordRepository>()
    private val notificationService = mockk<NotificationService>()
    private val dailyContentArchiveService = mockk<DailyContentArchiveService>()

    private val userService =
        UserService(
            userRepository = userRepository,
            categoryRepository = categoryRepository,
            reservedKeywordRepository = reservedKeywordRepository,
            notificationService = notificationService,
            dailyContentArchiveService = dailyContentArchiveService,
        )

    @Test
    fun `updatePreferences should allow initial category setup without incrementing categoryChangeCount or deleting archive`() {
        val userId = 1L
        val user = User(id = userId, deviceToken = "token", categories = mutableSetOf())
        val categoryBE = Category(id = 1L, name = "BE")
        val keywordAI = ReservedKeyword(id = 10L, name = "AI")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { categoryRepository.findByNameIn(listOf("BE")) } returns listOf(categoryBE)
        every { reservedKeywordRepository.findByNameIn(listOf("AI")) } returns listOf(keywordAI)
        every { userRepository.save(user) } returns user

        userService.updatePreferences(userId, listOf("BE"), listOf("AI"))

        assertThat(user.categories).containsExactly(categoryBE)
        assertThat(user.categoryChangeCount).isEqualTo(0)
        assertThat(user.isCategoryChanged).isFalse()
        verify(exactly = 0) { dailyContentArchiveService.deleteByDateAndUserId(any(), any()) }
    }

    @Test
    fun `updatePreferences should increment categoryChangeCount and refresh archive when category is changed`() {
        val userId = 1L
        val categoryBE = Category(id = 1L, name = "BE")
        val categoryFE = Category(id = 2L, name = "FE")
        val user = User(id = userId, deviceToken = "token", categories = mutableSetOf(categoryBE), categoryChangeCount = 0)
        val keywordAI = ReservedKeyword(id = 10L, name = "AI")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { categoryRepository.findByNameIn(listOf("FE")) } returns listOf(categoryFE)
        every { reservedKeywordRepository.findByNameIn(listOf("AI")) } returns listOf(keywordAI)
        every { userRepository.save(user) } returns user
        every { dailyContentArchiveService.deleteByDateAndUserId(userId, LocalDate.now()) } answers { }

        userService.updatePreferences(userId, listOf("FE"), listOf("AI"))

        assertThat(user.categories).containsExactly(categoryFE)
        assertThat(user.categoryChangeCount).isEqualTo(1)
        assertThat(user.isCategoryChanged).isTrue()
        verify(exactly = 1) { dailyContentArchiveService.deleteByDateAndUserId(userId, LocalDate.now()) }
    }

    @Test
    fun `updatePreferences should throw exception when category change is attempted more than allowed limit`() {
        val userId = 1L
        val categoryFE = Category(id = 2L, name = "FE")
        val categoryAndroid = Category(id = 3L, name = "Android")
        val user =
            User(
                id = userId,
                deviceToken = "token",
                categories = mutableSetOf(categoryFE),
                categoryChangeCount = 1,
                isCategoryChanged = true
            )
        val keywordAI = ReservedKeyword(id = 10L, name = "AI")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { categoryRepository.findByNameIn(listOf("Android")) } returns listOf(categoryAndroid)
        every { reservedKeywordRepository.findByNameIn(listOf("AI")) } returns listOf(keywordAI)

        assertThatThrownBy {
            userService.updatePreferences(userId, listOf("Android"), listOf("AI"))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("직군 변경은 계정당 최대 1회만 가능합니다.")

        verify(exactly = 0) { dailyContentArchiveService.deleteByDateAndUserId(any(), any()) }
    }

    @Test
    fun `updatePreferences with same category should not consume change limit or refresh archive`() {
        val userId = 1L
        val categoryFE = Category(id = 2L, name = "FE")
        val user = User(id = userId, deviceToken = "token", categories = mutableSetOf(categoryFE), categoryChangeCount = 0)
        val keywordAI = ReservedKeyword(id = 10L, name = "AI")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { categoryRepository.findByNameIn(listOf("FE")) } returns listOf(categoryFE)
        every { reservedKeywordRepository.findByNameIn(listOf("AI")) } returns listOf(keywordAI)
        every { userRepository.save(user) } returns user

        userService.updatePreferences(userId, listOf("FE"), listOf("AI"))

        assertThat(user.categoryChangeCount).isEqualTo(0)
        assertThat(user.isCategoryChanged).isFalse()
        verify(exactly = 0) { dailyContentArchiveService.deleteByDateAndUserId(any(), any()) }
    }
}
