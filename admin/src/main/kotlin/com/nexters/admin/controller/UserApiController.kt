package com.nexters.admin.controller

import com.nexters.external.repository.DailyContentArchiveRepository
import com.nexters.external.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/users")
class UserApiController(
    private val userRepository: UserRepository,
    private val dailyContentArchiveRepository: DailyContentArchiveRepository
) {
    @GetMapping
    fun getAllUsers(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) deviceToken: String?,
        pageable: Pageable
    ): ResponseEntity<Page<UserResponse>> {
        val users =
            when {
                userId != null -> {
                    // Search by user ID
                    val userList =
                        userRepository
                            .findById(userId)
                            .map { listOf(it) }
                            .orElse(emptyList())
                    PageImpl(userList, pageable, userList.size.toLong())
                }
                deviceToken != null -> {
                    // Search by device token
                    val userList =
                        userRepository
                            .findByDeviceToken(deviceToken)
                            ?.let { listOf(it) }
                            ?: emptyList()
                    PageImpl(userList, pageable, userList.size.toLong())
                }
                else -> {
                    // Return all users with pagination
                    userRepository.findAll(pageable)
                }
            }

        val userResponses =
            users.map { user ->
                UserResponse(
                    id = user.id ?: 0,
                    deviceToken = user.deviceToken,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt
                )
            }

        return ResponseEntity.ok(userResponses)
    }

    @GetMapping("/statistics")
    fun getOverallStatistics(): ResponseEntity<OverallStatisticsResponse> {
        val totalUsers = userRepository.count()

        // Get all archives to calculate active users
        val allArchives = dailyContentArchiveRepository.findAll()
        val activeUserIds = allArchives.map { it.user.id }.toSet()
        val activeUsers = activeUserIds.size.toLong()

        return ResponseEntity.ok(
            OverallStatisticsResponse(
                totalUsers = totalUsers,
                activeUsers = activeUsers
            )
        )
    }

    @GetMapping("/most-active")
    fun getMostActiveUsers(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<MostActiveUserResponse>> {
        // Get all archives and group by user ID
        val allArchives = dailyContentArchiveRepository.findAll()

        val userArchiveCounts =
            allArchives
                .groupBy { it.user.id }
                .mapValues { (_, archives) ->
                    archives.size to archives.maxOfOrNull { it.date }
                }.entries
                .sortedByDescending { it.value.first }
                .take(limit)

        // Get user details for the most active users
        val mostActiveUsers =
            userArchiveCounts.mapNotNull { (userId, countAndDate) ->
                userRepository.findById(userId).orElse(null)?.let { user ->
                    MostActiveUserResponse(
                        userId = user.id ?: 0,
                        deviceToken = user.deviceToken,
                        archiveCount = countAndDate.first,
                        lastActiveDate = countAndDate.second
                    )
                }
            }

        return ResponseEntity.ok(mostActiveUsers)
    }

    @GetMapping("/activity-trend")
    fun getActivityTrend(
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<List<DailyActiveUserResponse>> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)

        // Get all archives
        val allArchives = dailyContentArchiveRepository.findAll()

        // Filter archives within the date range and group by date
        val dailyActiveUsers =
            allArchives
                .filter { it.date in startDate..endDate }
                .groupBy { it.date }
                .mapValues { (_, archives) -> archives.map { it.user.id }.toSet().size }
                .toSortedMap()

        // Fill in missing dates with 0 count
        val result = mutableListOf<DailyActiveUserResponse>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            result.add(
                DailyActiveUserResponse(
                    date = currentDate,
                    count = dailyActiveUsers[currentDate] ?: 0
                )
            )
            currentDate = currentDate.plusDays(1)
        }

        return ResponseEntity.ok(result)
    }

    @GetMapping("/{userId}")
    fun getUserById(
        @PathVariable userId: Long
    ): ResponseEntity<UserDetailResponse> {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NoSuchElementException("User not found with id: $userId") }

        return ResponseEntity.ok(
            UserDetailResponse(
                id = user.id ?: 0,
                deviceToken = user.deviceToken,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        )
    }

    @GetMapping("/{userId}/statistics")
    fun getUserStatistics(
        @PathVariable userId: Long
    ): ResponseEntity<UserStatisticsResponse> {
        // Verify user exists
        userRepository
            .findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }

        // Get all archives for this user
        val userArchives =
            dailyContentArchiveRepository
                .findAll()
                .filter { it.user.id == userId }
                .sortedBy { it.date }

        val totalDaysActive = userArchives.size
        val lastActiveDate = userArchives.maxOfOrNull { it.date }

        // Calculate activity streak (consecutive days)
        var activityStreak = 0
        if (userArchives.isNotEmpty()) {
            val dates = userArchives.map { it.date }.sortedDescending()
            var currentStreak = 1

            for (i in 0 until dates.size - 1) {
                val currentDate = dates[i]
                val nextDate = dates[i + 1]

                if (currentDate.minusDays(1) == nextDate) {
                    currentStreak++
                } else {
                    break
                }
            }
            activityStreak = currentStreak
        }

        return ResponseEntity.ok(
            UserStatisticsResponse(
                totalDaysActive = totalDaysActive,
                lastActiveDate = lastActiveDate,
                activityStreak = activityStreak
            )
        )
    }

    @GetMapping("/{userId}/categories")
    fun getUserCategories(
        @PathVariable userId: Long
    ): ResponseEntity<List<UserCategoryResponse>> {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { NoSuchElementException("User not found with id: $userId") }

        val categories =
            user.categories.map { category ->
                UserCategoryResponse(
                    categoryId = category.id ?: 0,
                    categoryName = category.name,
                    weight = 1 // Default weight, as the current schema doesn't store weights
                )
            }

        return ResponseEntity.ok(categories)
    }

    @GetMapping("/{userId}/archives")
    fun getUserArchiveHistory(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<List<ArchiveHistoryResponse>> {
        // Verify user exists
        userRepository
            .findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)

        // Get all archives for this user within the date range
        val userArchives =
            dailyContentArchiveRepository
                .findAll()
                .filter { it.user.id == userId && it.date in startDate..endDate }
                .groupBy { it.date }
                .mapValues { (_, archives) -> archives.sumOf { it.exposureContents.size } }

        // Create response for all dates in range
        val result = mutableListOf<ArchiveHistoryResponse>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            result.add(
                ArchiveHistoryResponse(
                    date = currentDate,
                    hasArchive = userArchives.containsKey(currentDate),
                    contentCount = userArchives[currentDate] ?: 0
                )
            )
            currentDate = currentDate.plusDays(1)
        }

        return ResponseEntity.ok(result)
    }
}

data class UserResponse(
    val id: Long,
    val deviceToken: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class OverallStatisticsResponse(
    val totalUsers: Long,
    val activeUsers: Long
)

data class MostActiveUserResponse(
    val userId: Long,
    val deviceToken: String,
    val archiveCount: Int,
    val lastActiveDate: LocalDate?
)

data class DailyActiveUserResponse(
    val date: LocalDate,
    val count: Int
)

data class UserDetailResponse(
    val id: Long,
    val deviceToken: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class UserStatisticsResponse(
    val totalDaysActive: Int,
    val lastActiveDate: LocalDate?,
    val activityStreak: Int
)

data class UserCategoryResponse(
    val categoryId: Long,
    val categoryName: String,
    val weight: Int
)

data class ArchiveHistoryResponse(
    val date: LocalDate,
    val hasArchive: Boolean,
    val contentCount: Int
)
