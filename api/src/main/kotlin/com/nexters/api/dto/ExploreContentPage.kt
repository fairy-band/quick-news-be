package com.nexters.api.dto

data class ExploreContentPage(
    val contents: List<ExposureContentApiResponse>,
    val hasMore: Boolean,
    val nextOffset: Long?
)
