package com.nexters.admin.controller

import com.nexters.external.entity.Content
import com.nexters.external.entity.NewsletterSource
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.NewsletterSourceRepository
import com.nexters.external.repository.SummaryRepository
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.KeywordService
import com.nexters.newsletter.parser.MailContent
import com.nexters.newsletter.parser.MailParserFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

@Controller
@RequestMapping("/newsletter-sources")
class NewsletterSourceAdminController {
    @GetMapping
    fun getNewsletterSourcesPage(): String = "newsletter-sources"
}

@RestController
@RequestMapping("/api/newsletter-sources")
class NewsletterSourceApiController(
    private val newsletterSourceRepository: NewsletterSourceRepository,
    private val contentRepository: ContentRepository,
    private val summaryRepository: SummaryRepository,
    private val keywordService: KeywordService,
    private val exposureContentService: ExposureContentService
) {
    private val mailParserFactory = MailParserFactory()

    @GetMapping
    fun getAllNewsletterSources(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) senderEmail: String?,
        @RequestParam(required = false) subject: String?,
        @RequestParam(required = false) hasContent: Boolean?
    ): ResponseEntity<Page<NewsletterSourceWithContentStatus>> {
        val pageable =
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "receivedDate")
            )

        val newsletterSources =
            when {
                senderEmail != null -> {
                    val sources = newsletterSourceRepository.findBySenderEmail(senderEmail)
                    // Page로 변환
                    val start = page * size
                    val end = minOf(start + size, sources.size)
                    val content = sources.subList(start, end)
                    PageImpl(content, pageable, sources.size.toLong())
                }
                subject != null -> {
                    val sources = newsletterSourceRepository.findBySubjectContaining(subject)
                    // Page로 변환
                    val start = page * size
                    val end = minOf(start + size, sources.size)
                    val content = sources.subList(start, end)
                    PageImpl(content, pageable, sources.size.toLong())
                }
                else -> newsletterSourceRepository.findAll(pageable)
            }

        // 각 NewsletterSource에 대해 Content 존재 여부 확인
        val sourcesWithContentStatus =
            newsletterSources.content.map { source ->
                val hasContent =
                    source.id?.let { id ->
                        contentRepository.existsByNewsletterSourceId(id)
                    } ?: false
                NewsletterSourceWithContentStatus.from(source, hasContent)
            }

        val resultPage =
            PageImpl(
                sourcesWithContentStatus,
                pageable,
                newsletterSources.totalElements
            )

        return ResponseEntity.ok(resultPage)
    }

    @GetMapping("/{id}")
    fun getNewsletterSourceById(
        @PathVariable id: String
    ): ResponseEntity<NewsletterSourceWithContentStatus> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        val hasContent = contentRepository.existsByNewsletterSourceId(id)
        val sourceWithContentStatus = NewsletterSourceWithContentStatus.from(newsletterSource, hasContent)

        return ResponseEntity.ok(sourceWithContentStatus)
    }

    @PostMapping("/{id}/create-content")
    fun createContentFromNewsletterSource(
        @PathVariable id: String,
        @RequestBody request: CreateContentFromNewsletterSourceRequest
    ): ResponseEntity<Content> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        // RSS 소스의 경우 헤더에서 원본 URL 추출, 없으면 요청된 URL 사용
        val finalOriginalUrl = newsletterSource.headers["RSS-Item-URL"] ?: request.originalUrl

        val newContent =
            Content(
                newsletterSourceId = newsletterSource.id,
                title = request.title,
                content = request.content,
                newsletterName = request.newsletterName,
                originalUrl = finalOriginalUrl,
                publishedAt = request.publishedAt,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedContent = contentRepository.save(newContent)
        return ResponseEntity.ok(savedContent)
    }

    @GetMapping("/sender-emails")
    fun getSenderEmails(): ResponseEntity<List<String>> {
        val senderEmails =
            newsletterSourceRepository
                .findAll()
                .map { it.senderEmail }
                .distinct()
                .sorted()
        return ResponseEntity.ok(senderEmails)
    }

    @GetMapping("/subjects")
    fun getSubjects(): ResponseEntity<List<String>> {
        val subjects =
            newsletterSourceRepository
                .findAll()
                .mapNotNull { it.subject }
                .distinct()
                .sorted()
        return ResponseEntity.ok(subjects)
    }

    @PutMapping("/{id}")
    fun updateNewsletterSource(
        @PathVariable id: String,
        @RequestBody request: UpdateNewsletterSourceRequest
    ): ResponseEntity<NewsletterSource> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        val updatedNewsletterSource =
            newsletterSource.copy(
                subject = request.subject,
                content = request.content,
                sender = request.sender,
                senderEmail = request.senderEmail,
                recipient = request.recipient,
                recipientEmail = request.recipientEmail,
                contentType = request.contentType,
                updatedAt = LocalDateTime.now()
            )

        val savedNewsletterSource = newsletterSourceRepository.save(updatedNewsletterSource)
        return ResponseEntity.ok(savedNewsletterSource)
    }

    @PostMapping("/{id}/test-parser")
    fun testParser(
        @PathVariable id: String
    ): ResponseEntity<ParserTestResult> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        val parser = mailParserFactory.findParser(newsletterSource.senderEmail)
        val isTarget = parser != null

        val parsedContents =
            if (isTarget && parser != null) {
                try {
                    parser.parse(newsletterSource.content)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

        val result =
            ParserTestResult(
                isTarget = isTarget,
                parsedContents = parsedContents,
                originalContent = newsletterSource.content,
                senderEmail = newsletterSource.senderEmail,
                parserName = parser?.javaClass?.simpleName ?: "None"
            )

        return ResponseEntity.ok(result)
    }

    @PostMapping("/{id}/add-parsed-content")
    fun addParsedContent(
        @PathVariable id: String,
        @RequestBody request: AddParsedContentRequest
    ): ResponseEntity<Content> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        // RSS 소스의 경우 헤더에서 원본 URL 추출, 없으면 요청된 URL 사용
        val finalOriginalUrl = newsletterSource.headers["RSS-Item-URL"] ?: request.originalUrl

        val newContent =
            Content(
                newsletterSourceId = newsletterSource.id,
                title = request.title,
                content = request.content,
                newsletterName = request.newsletterName,
                originalUrl = finalOriginalUrl,
                publishedAt = request.publishedAt,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        val savedContent = contentRepository.save(newContent)
        return ResponseEntity.ok(savedContent)
    }

    @PostMapping("/{id}/match-keywords")
    fun matchKeywordsForNewsletterSource(
        @PathVariable id: String
    ): ResponseEntity<MatchKeywordsResponse> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        // Get all contents created from this newsletter source
        val contents = contentRepository.findByNewsletterSourceId(id)

        if (contents.isEmpty()) {
            return ResponseEntity.badRequest().body(
                MatchKeywordsResponse(
                    success = false,
                    message = "이 뉴스레터 소스에서 생성된 컨텐츠가 없습니다. 먼저 컨텐츠를 생성해주세요.",
                    totalContents = 0,
                    processedContents = 0,
                    matchedKeywords = emptyList()
                )
            )
        }

        val allMatchedKeywords = mutableSetOf<String>()
        var processedCount = 0

        contents.forEach { content ->
            try {
                val matchedKeywords = keywordService.matchReservedKeywords(content.content)
                matchedKeywords.forEach { keyword ->
                    allMatchedKeywords.add(keyword.name)
                }
                processedCount++
            } catch (e: Exception) {
                // 개별 컨텐츠 처리 실패는 로그만 남기고 계속 진행
                println("Failed to match keywords for content ${content.id}: ${e.message}")
            }
        }

        return ResponseEntity.ok(
            MatchKeywordsResponse(
                success = true,
                message = "키워드 매칭이 완료되었습니다.",
                totalContents = contents.size,
                processedContents = processedCount,
                matchedKeywords = allMatchedKeywords.toList()
            )
        )
    }

    @PostMapping("/{id}/create-exposure-content")
    fun createExposureContentForNewsletterSource(
        @PathVariable id: String
    ): ResponseEntity<CreateExposureContentResponse> {
        val newsletterSource =
            newsletterSourceRepository
                .findById(id)
                .orElseThrow { NoSuchElementException("NewsletterSource not found with id: $id") }

        // Get all contents created from this newsletter source
        val contents = contentRepository.findByNewsletterSourceId(id)

        if (contents.isEmpty()) {
            return ResponseEntity.badRequest().body(
                CreateExposureContentResponse(
                    success = false,
                    message = "이 뉴스레터 소스에서 생성된 컨텐츠가 없습니다. 먼저 컨텐츠를 생성해주세요.",
                    processedContents = 0,
                    createdExposureContents = emptyList()
                )
            )
        }

        val createdExposureContents = mutableListOf<ExposureContentInfo>()

        contents.forEach { content ->
            try {
                // Check if the content has any summaries
                val summaries = summaryRepository.findByContent(content)

                if (summaries.isNotEmpty()) {
                    // Use the latest summary to create exposure content
                    val latestSummary = summaries.first()
                    val exposureContent = exposureContentService.createExposureContentFromSummary(latestSummary.id!!)

                    createdExposureContents.add(
                        ExposureContentInfo(
                            contentId = content.id!!,
                            contentTitle = content.title,
                            exposureContentId = exposureContent.id!!,
                            provocativeKeyword = exposureContent.provocativeKeyword,
                            provocativeHeadline = exposureContent.provocativeHeadline
                        )
                    )
                } else {
                    // If no summary exists, create a basic exposure content using the content directly
                    val exposureContent =
                        exposureContentService.createOrUpdateExposureContent(
                            content = content,
                            provocativeKeyword = "Newsletter",
                            provocativeHeadline = content.title,
                            summaryContent = content.content.take(500) + if (content.content.length > 500) "..." else ""
                        )

                    createdExposureContents.add(
                        ExposureContentInfo(
                            contentId = content.id!!,
                            contentTitle = content.title,
                            exposureContentId = exposureContent.id!!,
                            provocativeKeyword = exposureContent.provocativeKeyword,
                            provocativeHeadline = exposureContent.provocativeHeadline
                        )
                    )
                }
            } catch (e: Exception) {
                // 개별 컨텐츠 처리 실패는 로그만 남기고 계속 진행
                println("Failed to create exposure content for content ${content.id}: ${e.message}")
            }
        }

        return ResponseEntity.ok(
            CreateExposureContentResponse(
                success = true,
                message = "노출 컨텐츠 생성이 완료되었습니다.",
                processedContents = createdExposureContents.size,
                createdExposureContents = createdExposureContents
            )
        )
    }
}

data class CreateContentFromNewsletterSourceRequest(
    val title: String,
    val content: String,
    val newsletterName: String,
    val originalUrl: String,
    val publishedAt: LocalDate
)

data class UpdateNewsletterSourceRequest(
    val subject: String?,
    val content: String,
    val sender: String,
    val senderEmail: String,
    val recipient: String,
    val recipientEmail: String,
    val contentType: String
)

data class ParserTestResult(
    val isTarget: Boolean,
    val parsedContents: List<MailContent>,
    val originalContent: String,
    val senderEmail: String,
    val parserName: String
)

data class AddParsedContentRequest(
    val title: String,
    val content: String,
    val newsletterName: String,
    val originalUrl: String,
    val publishedAt: LocalDate
)

data class MatchKeywordsResponse(
    val success: Boolean,
    val message: String,
    val totalContents: Int,
    val processedContents: Int,
    val matchedKeywords: List<String>
)

data class CreateExposureContentResponse(
    val success: Boolean,
    val message: String,
    val processedContents: Int,
    val createdExposureContents: List<ExposureContentInfo>
)

data class ExposureContentInfo(
    val contentId: Long,
    val contentTitle: String,
    val exposureContentId: Long,
    val provocativeKeyword: String,
    val provocativeHeadline: String
)

data class NewsletterSourceWithContentStatus(
    val id: String?,
    val subject: String?,
    val sender: String,
    val senderEmail: String,
    val recipient: String,
    val recipientEmail: String,
    val plainText: String?,
    val htmlText: String?,
    val content: String?,
    val contentType: String,
    val receivedDate: LocalDateTime,
    val headers: Map<String, String>,
    val attachments: List<Any>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val hasContent: Boolean
) {
    companion object {
        fun from(
            newsletterSource: NewsletterSource,
            hasContent: Boolean
        ): NewsletterSourceWithContentStatus =
            NewsletterSourceWithContentStatus(
                id = newsletterSource.id,
                subject = newsletterSource.subject,
                sender = newsletterSource.sender,
                senderEmail = newsletterSource.senderEmail,
                recipient = newsletterSource.recipient,
                recipientEmail = newsletterSource.recipientEmail,
                plainText = newsletterSource.content,
                htmlText = newsletterSource.content,
                content = newsletterSource.content,
                contentType = newsletterSource.contentType,
                receivedDate = newsletterSource.receivedDate,
                headers = newsletterSource.headers,
                attachments = newsletterSource.attachments,
                createdAt = newsletterSource.createdAt,
                updatedAt = newsletterSource.updatedAt,
                hasContent = hasContent
            )
    }
}
