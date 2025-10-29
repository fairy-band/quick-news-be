package com.nexters.api.dto

data class UpdateUserPreferenceApiRequest(
    val preferences: List<Preference>,
    val workingExperience: WorkingExperience,
)

enum class Preference(
    val categoryName: String
) {
    FRONTEND("FE"),
    BACKEND("BE"),
    ANDROID("Android"),
    IOS("iOS"),
    DEVOPS("DevOps")
}

enum class WorkingExperience(
    private val description: String,
    val keywordName: String
) {
    STUDENT("대학생, 취준생", "student"),
    JUNIOR("1 ~ 3년차 미만", "junior"),
    MID("3 ~ 5년차", "mid"),
    SENIOR("4 ~ 7년차", "senior"),
    EXPERT("10년차 이상", "expert")
}
