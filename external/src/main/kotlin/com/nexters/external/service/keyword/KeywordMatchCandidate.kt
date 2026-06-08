package com.nexters.external.service.keyword

import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.enums.KeywordAliasMatchType
import com.nexters.external.enums.KeywordMatchSource

data class KeywordMatchCandidate(
    val keyword: ReservedKeyword,
    val score: Double,
    val confidence: Double,
    val source: KeywordMatchSource,
    val matchType: KeywordAliasMatchType,
    val matchedText: String?,
    val reason: String,
)

data class ContentKeywordAssignmentResult(
    val automaticKeywordCount: Int,
    val aiFallbackKeywordCount: Int,
    val acceptedKeywordCount: Int,
    val usedAiFallback: Boolean,
)
