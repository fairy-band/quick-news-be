package com.nexters.newsletterfeeder.parser

import com.nexters.external.entity.Content
import com.nexters.external.entity.NewsletterSource
import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

/**
 * React Status 뉴스레터 파서
 *
 * React Status는 주간 React 개발 뉴스레터로, 다음과 같은 섹션들로 구성
 * - 메인 기사들 (React 관련 주요 뉴스, 튜토리얼)
 * - IN BRIEF: 간단한 뉴스 항목들
 * - CODE, TOOLS & LIBRARIES: React 관련 도구 및 라이브러리
 * - ELSEWHERE IN JAVASCRIPT: JavaScript 생태계 전반의 뉴스
 */
class ReactStatusParser : NewsletterParser {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReactStatusParser::class.java)
        private const val NEWSLETTER_NAME = "React Status"

        // 섹션 구분을 위한 패턴들
        private val MAIN_ARTICLE_PATTERN = Regex(
            """^\* ([A-Z0-9 .\-–—:]+)\s*\n\(\s*([^)]+)\s*\)\s*\n—?\s*(.+?)(?=^\* [A-Z]|^🤖|^IN BRIEF:|^🛠|^📢|$)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        private val IN_BRIEF_PATTERN = Regex(
            """IN BRIEF:\s*\n\n(.+?)(?=^\* [A-Z]|^🛠|^📢|$)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        private val TOOLS_SECTION_PATTERN = Regex(
            """🛠\s+CODE,?\s*TOOLS\s*&?\s*LIBRARIES\s*\n(.+?)(?=^📢|$)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        private val ELSEWHERE_SECTION_PATTERN = Regex(
            """📢\s+ELSEWHERE\s+IN\s+JAVASCRIPT\s*\n(.+?)(?=^-{10,}|$)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        // URL 추출 패턴
        private val URL_PATTERN = Regex("""\(\s*(https?://[^)\s]+)\s*\)""")

        // 스폰서 콘텐츠 식별 패턴
        private val SPONSOR_PATTERN = Regex("""\(SPONSOR\)|\bsponsor\b""", RegexOption.IGNORE_CASE)
    }

    override fun parse(emailMessage: EmailMessage): ParsedNewsletter {
        LOGGER.info("React Status 뉴스레터 파싱 시작: ${emailMessage.subject}")

        val textContent = emailMessage.textContent ?: emailMessage.extractedContent

        // NewsletterSource 생성
        val newsletterSource = createNewsletterSource(emailMessage)

        // 컨텐츠 파싱
        val contents = parseContents(textContent, newsletterSource.id!!)

        LOGGER.info("React Status 파싱 완료: ${contents.size}개 기사 추출")

        return ParsedNewsletter(
            source = newsletterSource,
            contents = contents
        )
    }

    override fun supports(sender: String): Boolean = sender == "react@cooperpress.com"


    private fun createNewsletterSource(emailMessage: EmailMessage): NewsletterSource {
        val senderInfo = parseSenderInfo(emailMessage.from.firstOrNull() ?: "")

        return NewsletterSource(
            id = UUID.randomUUID().toString(),
            subject = emailMessage.subject,
            sender = senderInfo.first,
            senderEmail = senderInfo.second,
            recipient = "newsletter reader",
            recipientEmail = "unknown",
            content = emailMessage.extractedContent,
            contentType = emailMessage.contentType ?: "text/plain",
            receivedDate = emailMessage.receivedDate ?: LocalDateTime.now(),
            headers = emptyMap(),
            attachments = emailMessage.attachments.map { attachment ->
                com.nexters.external.entity.Attachment(
                    filename = attachment.fileName ?: "unknown",
                    contentType = attachment.contentType ?: "application/octet-stream",
                    size = attachment.size ?: 0L,
                    data = attachment.data
                )
            }
        )
    }

    private fun parseSenderInfo(fromString: String): Pair<String, String> {
        // "React Status <react@cooperpress.com>" 형태에서 이름과 이메일 추출
        val emailPattern = Regex("""(.+?)\s*<([^>]+)>""")
        val match = emailPattern.find(fromString)

        return if (match != null) {
            val name = match.groupValues[1].trim()
            val email = match.groupValues[2].trim()
            name to email
        } else {
            fromString to fromString
        }
    }

    private fun parseContents(textContent: String, newsletterSourceId: String): List<Content> {
        val contents = mutableListOf<Content>()

        try {
            // 1. 메인 기사들 파싱
            parseMainArticles(textContent, newsletterSourceId, contents, textContent)

            // 2. IN BRIEF 섹션 파싱
            parseInBriefSection(textContent, newsletterSourceId, contents, textContent)

            // 3. CODE, TOOLS & LIBRARIES 섹션 파싱
            parseToolsSection(textContent, newsletterSourceId, contents, textContent)

            // 4. ELSEWHERE IN JAVASCRIPT 섹션 파싱
            parseElsewhereSection(textContent, newsletterSourceId, contents, textContent)

        } catch (e: Exception) {
            LOGGER.error("React Status 컨텐츠 파싱 중 오류 발생", e)
            // 파싱 실패 시 전체 내용을 하나의 컨텐츠로 처리
            contents.add(createFallbackContent(textContent, newsletterSourceId))
        }

        return contents
    }

    private fun parseMainArticles(
        textContent: String,
        newsletterSourceId: String,
        contents: MutableList<Content>,
        fullContent: String
    ) {
        val matches = MAIN_ARTICLE_PATTERN.findAll(textContent)

        matches.forEach { match ->
            val title = match.groupValues[1].trim()
            val articleText = match.value
            val url = extractUrl(articleText, fullContent)
            val description = match.groupValues[3].trim()

            // 스폰서 콘텐츠 제외 (옵션)
            if (!SPONSOR_PATTERN.containsMatchIn(description)) {
                contents.add(
                    Content(
                        newsletterSourceId = newsletterSourceId,
                        title = title,
                        content = description,
                        newsletterName = NEWSLETTER_NAME,
                        originalUrl = url,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                )
            }
        }

        LOGGER.debug("메인 기사 ${matches.count()}개 파싱 완료")
    }

    private fun parseInBriefSection(
        textContent: String,
        newsletterSourceId: String,
        contents: MutableList<Content>,
        fullContent: String
    ) {
        val inBriefMatch = IN_BRIEF_PATTERN.find(textContent)
        if (inBriefMatch != null) {
            val briefContent = inBriefMatch.groupValues[1]
            val briefItems = extractBriefItems(briefContent, fullContent)

            briefItems.forEach { item ->
                contents.add(
                    Content(
                        newsletterSourceId = newsletterSourceId,
                        title = "${NEWSLETTER_NAME} - Brief: ${item.title}",
                        content = item.content,
                        newsletterName = NEWSLETTER_NAME,
                        originalUrl = item.url,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                )
            }

            LOGGER.debug("IN BRIEF 섹션에서 ${briefItems.size}개 항목 파싱 완료")
        }
    }

    private fun parseToolsSection(
        textContent: String,
        newsletterSourceId: String,
        contents: MutableList<Content>,
        fullContent: String
    ) {
        val toolsMatch = TOOLS_SECTION_PATTERN.find(textContent)
        if (toolsMatch != null) {
            val toolsContent = toolsMatch.groupValues[1]
            val toolItems = extractToolItems(toolsContent, fullContent)

            toolItems.forEach { item ->
                contents.add(
                    Content(
                        newsletterSourceId = newsletterSourceId,
                        title = "${NEWSLETTER_NAME} - Tool: ${item.title}",
                        content = item.content,
                        newsletterName = NEWSLETTER_NAME,
                        originalUrl = item.url,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                )
            }

            LOGGER.debug("TOOLS 섹션에서 ${toolItems.size}개 항목 파싱 완료")
        }
    }

    private fun parseElsewhereSection(
        textContent: String,
        newsletterSourceId: String,
        contents: MutableList<Content>,
        fullContent: String
    ) {
        val elsewhereMatch = ELSEWHERE_SECTION_PATTERN.find(textContent)
        if (elsewhereMatch != null) {
            val elsewhereContent = elsewhereMatch.groupValues[1]
            val elsewhereItems = extractElsewhereItems(elsewhereContent, fullContent)

            elsewhereItems.forEach { item ->
                contents.add(
                    Content(
                        newsletterSourceId = newsletterSourceId,
                        title = "$NEWSLETTER_NAME - JS News: ${item.title}",
                        content = item.content,
                        newsletterName = NEWSLETTER_NAME,
                        originalUrl = item.url,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                )
            }

            LOGGER.debug("ELSEWHERE 섹션에서 ${elsewhereItems.size}개 항목 파싱 완료")
        }
    }

    // extractUrl 개선: 기사 텍스트와 본문 전체에서 가장 가까운 URL을 찾음
    private fun extractUrl(articleText: String, fullContent: String): String {
        val index = fullContent.indexOf(articleText)
        if (index >= 0) {
            val after = fullContent.substring(index, minOf(index + 300, fullContent.length))
            val urlMatch = URL_PATTERN.find(after)
            if (urlMatch != null) return urlMatch.groupValues[1]
        }
        val urlMatch = URL_PATTERN.find(articleText)
        return urlMatch?.groupValues?.get(1) ?: "https://react.statuscode.com"
    }

    private fun extractBriefItems(briefContent: String, fullContent: String): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()

        // * 로 시작하는 각 항목을 파싱
        val itemPattern = Regex(
            """^\*\s*(.+?)(?=^\*|\Z)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        itemPattern.findAll(briefContent).forEach { match ->
            val itemText = match.groupValues[1].trim()
            val url = extractUrl(itemText, fullContent)
            val title = extractTitleFromBrief(itemText)

            items.add(
                ParsedItem(
                    title = title,
                    content = itemText,
                    url = url
                )
            )
        }

        return items
    }

    private fun extractToolItems(toolsContent: String, fullContent: String): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()

        // * 로 시작하는 도구 항목들 파싱
        val toolPattern = Regex(
            """^\*\s*(.+?)(?=^\*|^📄|^\Z)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        toolPattern.findAll(toolsContent).forEach { match ->
            val itemText = match.groupValues[1].trim()
            val url = extractUrl(itemText, fullContent)
            val title = extractToolTitle(itemText)

            items.add(
                ParsedItem(
                    title = title,
                    content = itemText,
                    url = url
                )
            )
        }

        return items
    }

    private fun extractElsewhereItems(elsewhereContent: String, fullContent: String): List<ParsedItem> {
        val items = mutableListOf<ParsedItem>()

        // * 로 시작하는 JavaScript 뉴스 항목들 파싱
        val itemPattern = Regex(
            """^\*\s*(.+?)(?=^\*|\Z)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        itemPattern.findAll(elsewhereContent).forEach { match ->
            val itemText = match.groupValues[1].trim()
            val url = extractUrl(itemText, fullContent)
            val title = extractTitleFromBrief(itemText)

            items.add(
                ParsedItem(
                    title = title,
                    content = itemText,
                    url = url
                )
            )
        }

        return items
    }

    private fun extractTitleFromBrief(text: String): String {
        // 첫 번째 문장이나 URL 앞의 텍스트를 제목으로 사용
        return text.substringBefore("(")
            .substringBefore("–")
            .substringBefore("—")
            .trim()
            .take(100) // 제목 길이 제한
    }

    private fun extractToolTitle(text: String): String {
        // 도구 이름 추출 (대문자로 시작하는 부분)
        val titleMatch = Regex("""^([A-Z0-9\-.: ]+)""").find(text)
        return titleMatch?.groupValues?.get(1)?.trim()?.take(100)
            ?: extractTitleFromBrief(text)
    }

    private fun createFallbackContent(textContent: String, newsletterSourceId: String): Content {
        return Content(
            newsletterSourceId = newsletterSourceId,
            title = "$NEWSLETTER_NAME - Full Content",
            content = textContent,
            newsletterName = NEWSLETTER_NAME,
            originalUrl = "https://react.statuscode.com",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private data class ParsedItem(
        val title: String,
        val content: String,
        val url: String
    )
}
