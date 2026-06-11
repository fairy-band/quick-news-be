package com.nexters.api.batch.dto

import java.time.LocalDateTime

data class ContentDigestGroupingRequest(
    val dryRun: Boolean = true,
    val minCreatedAt: LocalDateTime? = null,
    val newsletterNames: Set<String>? = null,
    val newsletterSourceIds: Set<String>? = null,
    val minShortContentLength: Int = 500,
    val minShortItems: Int = 3,
    val minGroupContentLength: Int = 500,
    val maxGroupContentLength: Int = 10_000,
)

data class ContentDigestGroupingResponse(
    val dryRun: Boolean,
    val targetNewsletterNames: Set<String>,
    val scannedContents: Int,
    val scannedSourceGroups: Int,
    val skippedReferencedGroups: Int,
    val skippedNonGroupableGroups: Int,
    val groupedSourceGroups: Int,
    val groupedChunks: Int,
    val updatedContents: Int,
    val deletedContents: Int,
    val untouchedContentsInGroupedSources: Int,
    val estimatedRowReduction: Int,
    val samples: List<ContentDigestGroupingSample>,
)

data class ContentDigestGroupingSample(
    val newsletterName: String,
    val newsletterSourceId: String,
    val title: String,
    val contentIds: List<Long>,
    val groupedContentLength: Int,
)
