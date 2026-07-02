package com.nexters.api.controller

import com.nexters.api.dto.CreateUserApiRequest
import com.nexters.api.dto.Preference
import com.nexters.api.dto.UpdateUserPreferenceApiRequest
import com.nexters.api.dto.UserApiResponse
import com.nexters.api.dto.UserInfoApiResponse
import com.nexters.api.dto.WorkingExperience
import com.nexters.api.exception.UserNotFoundException
import com.nexters.external.service.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserApiController(
    private val userService: UserService,
) {
    @PostMapping("/register")
    fun register(
        @RequestBody request: CreateUserApiRequest,
    ): UserApiResponse = UserApiResponse(userService.register(request.deviceToken))

    @GetMapping("/login")
    fun login(deviceToken: String): UserApiResponse =
        userService.findUser(deviceToken)?.id?.let { UserApiResponse(it) }
            ?: throw UserNotFoundException("User not found with device token: $deviceToken")

    @PutMapping("/{userId}")
    fun updatePreferences(
        @PathVariable userId: Long,
        @RequestBody preferenceRequest: UpdateUserPreferenceApiRequest,
    ) {
        userService.updatePreferences(
            userId,
            preferenceRequest.preferences.map { it.categoryName },
            listOf(preferenceRequest.workingExperience.keywordName),
        )
    }

    @GetMapping("/{userId}")
    fun getUserInfo(
        @PathVariable userId: Long,
    ): UserInfoApiResponse {
        val user = userService.getUserById(userId)
        val userCategoryNames = user.categories.map { it.name }
        val userKeywordNames = user.keywords.map { it.name }

        val preferences = Preference.values().filter { userCategoryNames.contains(it.categoryName) }
        val workingExperience = WorkingExperience.values().firstOrNull { userKeywordNames.contains(it.keywordName) }

        return UserInfoApiResponse(
            id = user.id!!,
            preferences = preferences,
            workingExperience = workingExperience
        )
    }
}
