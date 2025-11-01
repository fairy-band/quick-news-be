package com.nexters.admin.controller

import com.nexters.external.entity.CandidateKeyword
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.KeywordService
import com.nexters.external.service.SummaryService
import com.nexters.newsletter.service.NewsletterProcessingService
import com.nexters.newsletter.service.RssContentService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/process")
class ProcessController(
    private val contentRepository: ContentRepository,
    private val summaryService: SummaryService,
    private val keywordService: KeywordService,
    private val reservedKeywordRepository: ReservedKeywordRepository,
    private val contentKeywordMappingRepository: ContentKeywordMappingRepository,
    private val summaryRepository: SummaryRepository,
    private val candidateKeywordRepository: CandidateKeywordRepository,
    private val exposureContentService: ExposureContentService,
    private val rssContentService: RssContentService,
    private val newsletterProcessingService: NewsletterProcessingService,
) {
    @GetMapping("/content/{contentId}/keywords")
    fun getContentKeywords(
        @PathVariable contentId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<ReservedKeyword>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val mappings = contentKeywordMappingRepository.findByContent(content, pageable)
        val keywordPage = mappings.map { it.keyword }

        return ResponseEntity.ok(keywordPage)
    }

    @GetMapping("/reserved-keywords")
    fun getAllReservedKeywords(pageable: Pageable): ResponseEntity<Page<ReservedKeyword>> =
        ResponseEntity.ok(reservedKeywordRepository.findAll(pageable))

    @PostMapping("/content/{contentId}/keywords")
    fun extractKeywords(
        @PathVariable contentId: Long
    ): ResponseEntity<KeywordResponse> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val keywordResult = keywordService.extractKeywords(emptyList(), content.content)

        // Save matched keywords as content-keyword mappings
        keywordResult.matchedKeywords.forEach { keywordName ->
            val keyword = reservedKeywordRepository.findByName(keywordName)
            if (keyword != null) {
                val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
                if (existingMapping == null) {
                    val mapping =
                        ContentKeywordMapping(
                            content = content,
                            keyword = keyword,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    contentKeywordMappingRepository.save(mapping)
                }
            }
        }

        return ResponseEntity.ok(
            KeywordResponse(
                success = true,
                matchedKeywords = keywordResult.matchedKeywords,
                suggestedKeywords = keywordResult.suggestedKeywords,
                provocativeKeywords = keywordResult.provocativeKeywords
            )
        )
    }

    @PostMapping("/content/{contentId}/keywords/extract-with-selected")
    fun extractKeywordsWithSelected(
        @PathVariable contentId: Long,
        @RequestBody request: SelectedKeywordsRequest
    ): ResponseEntity<KeywordResponse> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val selectedKeywords =
            request.keywordIds.mapNotNull { keywordId ->
                reservedKeywordRepository.findById(keywordId).orElse(null)
            }

        val keywordNames = selectedKeywords.map { it.name }
        val keywordResult = keywordService.extractKeywords(keywordNames, content.content)

        // Save matched keywords as content-keyword mappings
        keywordResult.matchedKeywords.forEach { keywordName ->
            val keyword = reservedKeywordRepository.findByName(keywordName)
            if (keyword != null) {
                val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
                if (existingMapping == null) {
                    val mapping =
                        ContentKeywordMapping(
                            content = content,
                            keyword = keyword,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    contentKeywordMappingRepository.save(mapping)
                }
            }
        }

        return ResponseEntity.ok(
            KeywordResponse(
                success = true,
                matchedKeywords = keywordResult.matchedKeywords,
                suggestedKeywords = keywordResult.suggestedKeywords,
                provocativeKeywords = keywordResult.provocativeKeywords
            )
        )
    }

    @PostMapping("/content/{contentId}/keywords/extract-with-custom")
    fun extractKeywordsWithCustom(
        @PathVariable contentId: Long,
        @RequestBody request: CustomKeywordsRequest
    ): ResponseEntity<KeywordResponse> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val keywordResult = keywordService.extractKeywords(request.keywords, content.content)

        // Save matched keywords as content-keyword mappings
        keywordResult.matchedKeywords.forEach { keywordName ->
            val keyword = reservedKeywordRepository.findByName(keywordName)
            if (keyword != null) {
                val existingMapping = contentKeywordMappingRepository.findByContentAndKeyword(content, keyword)
                if (existingMapping == null) {
                    val mapping =
                        ContentKeywordMapping(
                            content = content,
                            keyword = keyword,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    contentKeywordMappingRepository.save(mapping)
                }
            }
        }

        return ResponseEntity.ok(
            KeywordResponse(
                success = true,
                matchedKeywords = keywordResult.matchedKeywords,
                suggestedKeywords = keywordResult.suggestedKeywords,
                provocativeKeywords = keywordResult.provocativeKeywords
            )
        )
    }

    @PostMapping("/content/{contentId}/summary")
    fun generateSummary(
        @PathVariable contentId: Long
    ): ResponseEntity<SummaryResponse> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val summaryResult = summaryService.summarize(content.content)

        return ResponseEntity.ok(
            SummaryResponse(
                success = true,
                summary = summaryResult.summary,
                provocativeHeadlines = summaryResult.provocativeHeadlines,
                usedModel = summaryResult.usedModel?.modelName ?: "unknown"
            )
        )
    }

    @PostMapping("/content/{contentId}/summary/save")
    fun saveSummary(
        @PathVariable contentId: Long,
        @RequestBody request: SaveSummaryRequest
    ): ResponseEntity<SaveSummaryResponse> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val summary =
            Summary(
                content = content,
                title = request.title,
                summarizedContent = request.summary,
                model = request.model,
                summarizedAt = LocalDateTime.now(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedSummary = summaryRepository.save(summary)

        return ResponseEntity.ok(
            SaveSummaryResponse(
                success = true,
                summaryId = savedSummary.id
            )
        )
    }

    @GetMapping("/content/{contentId}/summaries")
    fun getContentSummaries(
        @PathVariable contentId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<SummaryDetailResponse>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val summaries = summaryRepository.findByContent(content, pageable)

        val summaryResponses =
            summaries.map { summary ->
                SummaryDetailResponse(
                    id = summary.id ?: 0,
                    title = summary.title,
                    summary = summary.summarizedContent,
                    model = summary.model,
                    summarizedAt = summary.summarizedAt,
                    createdAt = summary.createdAt
                )
            }

        return ResponseEntity.ok(summaryResponses)
    }

    @PostMapping("/candidate-keywords")
    fun addCandidateKeywords(
        @RequestBody request: AddCandidateKeywordsRequest
    ): ResponseEntity<AddCandidateKeywordsResponse> {
        val addedKeywords = mutableListOf<String>()
        val existingKeywords = mutableListOf<String>()
        val errorKeywords = mutableListOf<String>()

        request.keywords.forEach { keyword ->
            try {
                // Check if keyword already exists as a candidate
                val existingCandidate = candidateKeywordRepository.findByName(keyword)
                if (existingCandidate != null) {
                    existingKeywords.add(keyword)
                    return@forEach
                }

                // Check if keyword already exists as a reserved keyword
                val existingReserved = reservedKeywordRepository.findByName(keyword)
                if (existingReserved != null) {
                    existingKeywords.add(keyword)
                    return@forEach
                }

                // Create new candidate keyword
                val candidateKeyword =
                    CandidateKeyword(
                        name = keyword,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                candidateKeywordRepository.save(candidateKeyword)
                addedKeywords.add(keyword)
            } catch (e: Exception) {
                errorKeywords.add(keyword)
            }
        }

        return ResponseEntity.ok(
            AddCandidateKeywordsResponse(
                success = addedKeywords.isNotEmpty() || existingKeywords.isNotEmpty(),
                addedKeywords = addedKeywords,
                existingKeywords = existingKeywords,
                errorKeywords = errorKeywords
            )
        )
    }

    @GetMapping("/summary/{summaryId}/exposure-content-exists")
    fun checkExposureContentExists(
        @PathVariable summaryId: Long
    ): ResponseEntity<Map<String, Any>> {
        val summary =
            summaryRepository
                .findById(summaryId)
                .orElseThrow { NoSuchElementException("Summary not found with ID: $summaryId") }

        val exposureContent =
            exposureContentService.getExposureContentByContent(summary.content)

        // Check if the exposure content's summary content matches this summary's content
        // This is a simple way to determine if this summary is the active one
        val isThisSummaryActive =
            exposureContent?.let {
                // Compare summary content with exposure content's summary content
                // We use trim() to ignore whitespace differences
                it.summaryContent.trim() == summary.summarizedContent.trim()
            } ?: false

        val response =
            mutableMapOf<String, Any>(
                "exists" to isThisSummaryActive
            )

        // Add the exposure content ID if this summary is active
        if (isThisSummaryActive) {
            exposureContent?.id?.let {
                response["exposureContentId"] = it
            }
        }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/content/{contentId}/exposure-status")
    fun checkContentExposureStatus(
        @PathVariable contentId: Long
    ): ResponseEntity<Map<String, Any>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val exposureContent = exposureContentService.getExposureContentByContent(content)
        val isExposed = exposureContent != null

        val response =
            mutableMapOf<String, Any>(
                "isExposed" to isExposed
            )

        // Add the exposure content ID if it exists
        if (isExposed) {
            exposureContent?.id?.let {
                response["exposureContentId"] = it
            }
        }

        return ResponseEntity.ok(response)
    }

    @PostMapping("/contents/exposure-status")
    fun getBulkContentExposureStatus(
        @RequestBody request: BulkContentExposureStatusRequest
    ): ResponseEntity<Map<Long, Boolean>> {
        val contentIds = request.contentIds
        val result = mutableMapOf<Long, Boolean>()

        // Find all contents at once
        val contents = contentRepository.findAllById(contentIds)

        // Check exposure status for each content
        contents.forEach { content ->
            val isExposed = exposureContentService.getExposureContentByContent(content) != null
            content.id?.let {
                result[it] = isExposed
            }
        }

        return ResponseEntity.ok(result)
    }

    @PostMapping("/rss/process-ai")
    fun processRssWithAi(): ResponseEntity<RssProcessingResponse> =
        try {
            val result = rssContentService.processDailyRssWithAi()
            ResponseEntity.ok(
                RssProcessingResponse(
                    success = true,
                    message = result.message,
                    processedCount = result.processedCount,
                    errorCount = result.errorCount,
                    totalProcessedToday = result.totalProcessedToday
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(
                RssProcessingResponse(
                    success = false,
                    message = "RSS AI processing failed: ${e.message}",
                    processedCount = 0,
                    errorCount = 1,
                    totalProcessedToday = 0
                )
            )
        }

    @GetMapping("/rss/stats")
    fun getRssProcessingStats(): ResponseEntity<RssStatsResponse> {
        val stats = rssContentService.getProcessingStats()
        return ResponseEntity.ok(
            RssStatsResponse(
                processedToday = stats.processedToday,
                dailyLimit = stats.dailyLimit,
                pending = stats.pending,
                remainingQuota = stats.remainingQuota
            )
        )
    }

    @PostMapping("/content/{contentId}/auto-process")
    fun autoProcessContent(
        @PathVariable contentId: Long
    ): ResponseEntity<AutoProcessResponse> =
        try {
            val content = contentRepository.findById(contentId).orElseThrow()
            val exposureContent = newsletterProcessingService.processExistingContent(content)

            ResponseEntity.ok(
                AutoProcessResponse(
                    success = true,
                    message = "콘텐츠가 성공적으로 자동 처리되었습니다.",
                    exposureContentId = exposureContent?.id
                )
            )
        } catch (e: Exception) {
            ResponseEntity.ok(
                AutoProcessResponse(
                    success = false,
                    message = "자동 처리 중 오류가 발생했습니다: ${e.message}",
                    exposureContentId = null
                )
            )
        }
}

data class SummaryResponse(
    val success: Boolean,
    val summary: String,
    val provocativeHeadlines: List<String> = emptyList(),
    val usedModel: String = "unknown",
    val error: String? = null
)

data class KeywordResponse(
    val success: Boolean,
    val matchedKeywords: List<String> = emptyList(),
    val suggestedKeywords: List<String> = emptyList(),
    val provocativeKeywords: List<String> = emptyList(),
    val error: String? = null
)

data class SelectedKeywordsRequest(
    val keywordIds: List<Long>
)

data class CustomKeywordsRequest(
    val keywords: List<String>
)

data class SaveSummaryRequest(
    val title: String,
    val summary: String,
    val model: String
)

data class SaveSummaryResponse(
    val success: Boolean,
    val summaryId: Long? = null,
    val error: String? = null
)

data class SummaryDetailResponse(
    val id: Long,
    val title: String,
    val summary: String,
    val model: String,
    val summarizedAt: LocalDateTime,
    val createdAt: LocalDateTime
)

data class AddCandidateKeywordsRequest(
    val keywords: List<String>
)

data class AddCandidateKeywordsResponse(
    val success: Boolean,
    val addedKeywords: List<String> = emptyList(),
    val existingKeywords: List<String> = emptyList(),
    val errorKeywords: List<String> = emptyList()
)

data class BulkContentExposureStatusRequest(
    val contentIds: List<Long>
)

data class RssProcessingResponse(
    val success: Boolean,
    val message: String,
    val processedCount: Int,
    val errorCount: Int,
    val totalProcessedToday: Int
)

data class RssStatsResponse(
    val processedToday: Int,
    val dailyLimit: Int,
    val pending: Int,
    val remainingQuota: Int
)

data class AutoProcessResponse(
    val success: Boolean,
    val message: String,
    val exposureContentId: Long? = null
)
