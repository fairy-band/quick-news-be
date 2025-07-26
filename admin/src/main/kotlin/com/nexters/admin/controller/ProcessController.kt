package com.nexters.admin.controller

import com.nexters.admin.repository.ContentKeywordMappingRepository
import com.nexters.admin.repository.ContentRepository
import com.nexters.external.entity.CandidateKeyword
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.repository.CandidateKeywordRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.service.KeywordService
import com.nexters.external.service.SummaryService
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
    private val candidateKeywordRepository: CandidateKeywordRepository
) {
    @GetMapping("/content/{contentId}/keywords")
    fun getContentKeywords(
        @PathVariable contentId: Long
    ): ResponseEntity<List<ReservedKeyword>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val mappings = contentKeywordMappingRepository.findByContent(content)
        val keywords = mappings.map { it.keyword }

        return ResponseEntity.ok(keywords)
    }

    @GetMapping("/reserved-keywords")
    fun getAllReservedKeywords(): ResponseEntity<List<ReservedKeyword>> = ResponseEntity.ok(reservedKeywordRepository.findAll())

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
        @PathVariable contentId: Long
    ): ResponseEntity<List<SummaryDetailResponse>> {
        val content =
            contentRepository
                .findById(contentId)
                .orElseThrow { NoSuchElementException("Content not found with id: $contentId") }

        val summaries = summaryRepository.findByContent(content)

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
