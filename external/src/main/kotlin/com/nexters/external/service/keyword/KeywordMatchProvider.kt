package com.nexters.external.service.keyword

import com.nexters.external.entity.Content
import com.nexters.external.entity.ReservedKeyword

interface KeywordMatchProvider {
    fun match(
        content: Content,
        reservedKeywords: List<ReservedKeyword>,
    ): List<KeywordMatchCandidate>
}
