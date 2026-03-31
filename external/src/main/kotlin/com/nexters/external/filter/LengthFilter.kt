package com.nexters.external.filter

import com.nexters.external.entity.Content

/**
 * Content 길이 기반 필터
 * - 최소 길이 이상
 * - 최대 길이 이하
 */
class LengthFilter(
    private val minLength: Int,
    private val maxLength: Int
) : ContentFilter {
    override fun filter(content: Content): FilterResult {
        val contentLength = content.content.length

        return when {
            contentLength < minLength -> {
                FilterResult.Fail("Content too short: $contentLength < $minLength")
            }
            contentLength > maxLength -> {
                FilterResult.Fail("Content too long: $contentLength > $maxLength")
            }
            else -> FilterResult.Pass
        }
    }

    override fun getName(): String = "LengthFilter(min=$minLength, max=$maxLength)"
}
