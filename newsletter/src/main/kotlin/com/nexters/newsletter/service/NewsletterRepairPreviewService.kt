package com.nexters.newsletter.service

import com.nexters.external.apiclient.CrawlerServiceClient
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentMarkdownRepository
import com.nexters.external.repository.ExposureContentRepository
import com.nexters.external.service.NewsletterSourceService
import com.nexters.newsletter.parser.MailContent
import com.nexters.newsletter.parser.MailParseContext
import com.nexters.newsletter.parser.MailParserFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * Read-only diagnostic view for a newsletter source.  It intentionally runs the
 * production parser instead of duplicating parser behaviour in the admin app.
 */
@Service
class NewsletterRepairPreviewService(
    private val newsletterSourceService: NewsletterSourceService,
    private val contentRepository: ContentRepository,
    private val exposureContentRepository: ExposureContentRepository,
    private val exposureContentMarkdownRepository: ExposureContentMarkdownRepository,
    private val crawlerServiceClient: CrawlerServiceClient? = null,
) {
    private val mailParserFactory = MailParserFactory(crawlerServiceClient)

    fun preview(sourceId: String): NewsletterRepairPreview {
        val source = requireNotNull(newsletterSourceService.findById(sourceId)) { "Newsletter source not found: $sourceId" }
        val parser = mailParserFactory.findParser(source.senderEmail, source.subject)
        val context = MailParseContext.from(source)
        val parsed = parser?.let { runCatching { it.parse(context) } }
        val storedContents = contentRepository.findByNewsletterSourceId(sourceId)

        return NewsletterRepairPreview(
            source =
                NewsletterRepairSourceSnapshot(
                    id = source.id.orEmpty(),
                    subject = source.subject,
                    sender = source.sender,
                    senderEmail = source.senderEmail,
                    recipient = source.recipient,
                    receivedAt = source.receivedDate.toString(),
                    textContent = source.content,
                    htmlContent = source.htmlContent,
                    enrichment =
                        source.enrichment?.webPage?.items.orEmpty().map { item ->
                            NewsletterRepairEnrichmentSnapshot(
                                url = item.url,
                                normalizedUrl = item.normalizedUrl,
                                title = item.title,
                                content = item.content,
                                status = item.status,
                                reason = item.reason,
                                fetchedAt = item.fetchedAt?.toString(),
                                contentHash = item.contentHash,
                            )
                        },
                ),
            parser =
                NewsletterRepairParserSnapshot(
                    name = parser?.javaClass?.simpleName,
                    supported = parser != null,
                    error = parsed?.exceptionOrNull()?.message,
                    outputs = parsed?.getOrDefault(emptyList()).orEmpty().map { it.toSnapshot() },
                ),
            storedContents =
                storedContents.map { content ->
                    val exposure = content.id?.let(exposureContentRepository::findByContentId)
                    val markdown = exposure?.id?.let(exposureContentMarkdownRepository::findByExposureContentId)
                    NewsletterRepairStoredContentSnapshot(
                        id = content.id ?: -1,
                        title = content.title,
                        originalUrl = content.originalUrl,
                        rawContent = content.content,
                        rawContentHash = content.content.sha256(),
                        exposureContentId = exposure?.id,
                        provocativeHeadline = exposure?.provocativeHeadline,
                        provocativeKeyword = exposure?.provocativeKeyword,
                        summaryContent = exposure?.summaryContent,
                        markdownId = markdown?.id,
                        markdownContent = markdown?.markdownContent,
                    )
                },
            diagnostics = diagnostics(parsed?.getOrDefault(emptyList()).orEmpty(), storedContents),
        )
    }

    private fun diagnostics(
        parsed: List<MailContent>,
        stored: List<com.nexters.external.entity.Content>,
    ): List<NewsletterRepairDiagnostic> {
        val messages = mutableListOf<NewsletterRepairDiagnostic>()
        val parsedUrls = parsed.map { it.link.trim() }.filter { it.isNotBlank() }.toSet()
        val storedUrls = stored.map { it.originalUrl.trim() }.filter { it.isNotBlank() }
        val rootUrls = storedUrls.filter { url -> url.matches(Regex("""https?://(?:www\.)?[^/]+/?""")) }

        if (rootUrls.isNotEmpty() && parsedUrls.any { it.matches(Regex("""https?://(?:www\.)?[^/]+/.+""")) }) {
            messages += NewsletterRepairDiagnostic("HIGH", "ROOT_URL", "저장 URL이 홈페이지인데 파서 결과에는 상세 URL이 있습니다.")
        }
        if (stored.size != parsed.size) {
            messages += NewsletterRepairDiagnostic("MEDIUM", "COUNT_MISMATCH", "현재 파서 결과 ${parsed.size}건, 저장 콘텐츠 ${stored.size}건입니다.")
        }
        if (storedUrls.groupingBy { it }.eachCount().any { it.value > 1 }) {
            messages += NewsletterRepairDiagnostic("HIGH", "DUPLICATE_URL", "같은 원본에서 동일 URL이 여러 콘텐츠에 저장되어 있습니다.")
        }
        if (stored.map { it.content.sha256() }.groupingBy { it }.eachCount().any { it.value > 1 }) {
            messages += NewsletterRepairDiagnostic("HIGH", "DUPLICATE_BODY", "같은 원본에서 동일 본문이 여러 콘텐츠에 저장되어 있습니다.")
        }
        if (stored.any { it.content.trim().matches(Regex("""\(cat /tmp/trans_\d+\.md\)""")) }) {
            messages += NewsletterRepairDiagnostic("HIGH", "PLACEHOLDER", "임시 파일 경로가 본문으로 저장되어 있습니다.")
        }
        if (parsed.size >= MANY_OUTPUTS_THRESHOLD) {
            messages += NewsletterRepairDiagnostic("LOW", "MANY_OUTPUTS", "파서가 ${parsed.size}건을 만들었습니다. 다건 뉴스레터일 수 있으므로 URL 중복 여부를 확인하세요.")
        }
        return messages
    }

    private fun MailContent.toSnapshot() =
        NewsletterRepairParserOutputSnapshot(
            title = title,
            content = content,
            url = link,
            imageUrl = imageUrl,
            section = section,
        )

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val MANY_OUTPUTS_THRESHOLD = 16
    }
}

data class NewsletterRepairPreview(
    val source: NewsletterRepairSourceSnapshot,
    val parser: NewsletterRepairParserSnapshot,
    val storedContents: List<NewsletterRepairStoredContentSnapshot>,
    val diagnostics: List<NewsletterRepairDiagnostic>,
)

data class NewsletterRepairSourceSnapshot(
    val id: String,
    val subject: String?,
    val sender: String,
    val senderEmail: String,
    val recipient: String,
    val receivedAt: String,
    val textContent: String,
    val htmlContent: String?,
    val enrichment: List<NewsletterRepairEnrichmentSnapshot>,
)

data class NewsletterRepairEnrichmentSnapshot(
    val url: String,
    val normalizedUrl: String?,
    val title: String?,
    val content: String?,
    val status: String,
    val reason: String?,
    val fetchedAt: String?,
    val contentHash: String?,
)

data class NewsletterRepairParserSnapshot(
    val name: String?,
    val supported: Boolean,
    val error: String?,
    val outputs: List<NewsletterRepairParserOutputSnapshot>,
)

data class NewsletterRepairParserOutputSnapshot(
    val title: String,
    val content: String,
    val url: String,
    val imageUrl: String?,
    val section: String?,
)

data class NewsletterRepairStoredContentSnapshot(
    val id: Long,
    val title: String,
    val originalUrl: String,
    val rawContent: String,
    val rawContentHash: String,
    val exposureContentId: Long?,
    val provocativeHeadline: String?,
    val provocativeKeyword: String?,
    val summaryContent: String?,
    val markdownId: Long?,
    val markdownContent: String?,
)

data class NewsletterRepairDiagnostic(
    val severity: String,
    val code: String,
    val message: String,
)
