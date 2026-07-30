package com.nexters.api.dto

data class UserInfoApiResponse(
    val id: Long,
    val preferences: List<Preference>,
    val workingExperience: WorkingExperience?,
    val isOnboarded: Boolean,
    val isCategoryChanged: Boolean = false,
    val categoryChangeCount: Int = 0,
)
