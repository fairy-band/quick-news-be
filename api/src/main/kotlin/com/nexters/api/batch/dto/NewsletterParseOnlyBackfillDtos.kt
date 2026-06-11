package com.nexters.api.batch.dto

data class NewsletterParseOnlyBackfillRequest(
    val dryRun: Boolean = true,
    val limit: Int? = null,
    val force: Boolean = false,
    val senderEmails: Set<String>? = null,
)

data class NewsletterParseOnlyBackfillResponse(
    val dryRun: Boolean,
    val force: Boolean,
    val targetAllParsers: Boolean,
    val senderEmails: Set<String>,
    val scannedSources: Int,
    val targetSources: Int,
    val candidateSources: Int,
    val skippedNoParser: Int,
    val skippedExistingSource: Int,
    val skippedInvalidContent: Int,
    val skippedDuplicateUrl: Int,
    val parseEmptySources: Int,
    val parseFailedSources: Int,
    val createdContents: Int,
    val wouldCreateContents: Int,
    val createdContentIds: List<Long>,
    val samples: List<NewsletterParseOnlyBackfillSample>,
    val failures: List<NewsletterParseOnlyBackfillFailure>,
)

data class NewsletterParseOnlyBackfillSample(
    val sourceId: String,
    val senderEmail: String,
    val subject: String?,
    val title: String,
    val originalUrl: String,
)

data class NewsletterParseOnlyBackfillFailure(
    val sourceId: String?,
    val senderEmail: String,
    val subject: String?,
    val reason: String,
)
