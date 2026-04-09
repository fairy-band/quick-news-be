package com.nexters.api.dto

data class CreateContentProviderRequestApiRequest(
    val contentProviderName: String,
    val channel: String,
    val requestCategory: String,
    val relatedTo: String,
    val reason: String? = null,
)
