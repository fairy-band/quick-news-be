package com.nexters.external.filter

import com.nexters.external.entity.Content
import com.nexters.external.enums.ContentProviderType

/**
 * ContentProvider 타입 기반 필터
 * - 허용된 provider type만 통과
 */
class ProviderTypeFilter(
    private val allowedTypes: Set<ContentProviderType>
) : ContentFilter {
    override fun filter(content: Content): FilterResult =
        when (val providerType = content.contentProvider?.type) {
            null -> {
                FilterResult.Fail("Content provider type is null")
            }
            !in allowedTypes -> {
                FilterResult.Fail("Provider type $providerType not allowed")
            }
            else -> FilterResult.Pass
        }

    override fun getName(): String = "ProviderTypeFilter(${allowedTypes.joinToString()})"
}
