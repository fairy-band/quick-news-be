package com.nexters.api.dto

data class CreateContentProviderRequestApiRequest(
    val contentProviderName: String,
    val channel: String,
    val requestCategories: List<String>,
    val relatedTo: String,
    val reason: String? = null,
)
