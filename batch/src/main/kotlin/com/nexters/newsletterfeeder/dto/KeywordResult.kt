package com.nexters.newsletterfeeder.dto

data class KeywordResult(
    val matchedKeywords: List<String>,
    val suggestedKeywords: List<String>,
    val provocativeKeywords: List<String>,
)
