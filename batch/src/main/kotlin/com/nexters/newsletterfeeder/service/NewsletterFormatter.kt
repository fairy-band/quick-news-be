package com.nexters.newsletterfeeder.service

import com.nexters.newsletterfeeder.dto.EmailMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import java.util.regex.Pattern

@Service
class NewsletterFormatter {

    fun formatNewsletterContent(emailMessage: EmailMessage): FormattedNewsletter {
        // 메일 형태 분석 로깅
        analyzeEmailStructure(emailMessage)

        val senderInfo = parseSenderInfo(emailMessage.from.firstOrNull() ?: "Unknown")
        val formattedContent = extractAndFormatContent(emailMessage)

        return FormattedNewsletter(
            subject = emailMessage.subject ?: "No Subject",
            sender = senderInfo.first,
            senderEmail = senderInfo.second,
            content = formattedContent.content,
            contentType = emailMessage.contentType ?: "text/plain",
            attachments = emailMessage.attachments.map { attachment ->
                FormattedAttachment(
                    filename = attachment.fileName ?: "unnamed",
                    contentType = attachment.contentType ?: "application/octet-stream",
                    size = attachment.size ?: 0
                )
            },
            receivedDate = emailMessage.receivedDate ?: emailMessage.sentDate,
            originalContent = emailMessage.extractedContent,
            articles = extractMultipleArticles(emailMessage)
        )
    }

    /**
     * 하나의 메일에서 여러 개의 뉴스레터 아티클을 추출합니다.
     */
    private fun extractMultipleArticles(emailMessage: EmailMessage): List<NewsletterArticle> {
        val content = when {
            emailMessage.htmlContent != null -> cleanHtmlContentForNewsletter(emailMessage.htmlContent)
            emailMessage.textContent != null -> cleanTextContentForNewsletter(emailMessage.textContent)
            else -> emailMessage.extractedContent
        }

        return extractArticlesFromContent(content)
    }

    /**
     * 콘텐츠에서 개별 아티클들을 추출합니다.
     */
    private fun extractArticlesFromContent(content: String): List<NewsletterArticle> {
        val articles = mutableListOf<NewsletterArticle>()

        // 1. 헤딩 기반 아티클 분리 (h1, h2, h3 태그)
        val headingBasedArticles = extractArticlesByHeadings(content)
        if (headingBasedArticles.isNotEmpty()) {
            articles.addAll(headingBasedArticles)
        }

        // 2. 섹션 기반 아티클 분리 (div, section 태그)
        val sectionBasedArticles = extractArticlesBySections(content)
        if (sectionBasedArticles.isNotEmpty()) {
            articles.addAll(sectionBasedArticles)
        }

        // 3. 구분자 기반 아티클 분리 (---, ===, *** 등)
        val separatorBasedArticles = extractArticlesBySeparators(content)
        if (separatorBasedArticles.isNotEmpty()) {
            articles.addAll(separatorBasedArticles)
        }

        // 4. 번호 기반 아티클 분리 (1., 2., 3. 등)
        val numberedArticles = extractArticlesByNumbering(content)
        if (numberedArticles.isNotEmpty()) {
            articles.addAll(numberedArticles)
        }

        // 아티클이 추출되지 않은 경우 전체 콘텐츠를 하나의 아티클로 처리
        if (articles.isEmpty()) {
            articles.add(NewsletterArticle(
                title = "Main Article",
                content = content,
                order = 1
            ))
        }

        // 중복 제거 및 정렬
        return articles.distinctBy { it.content }.sortedBy { it.order }
    }

    /**
     * 헤딩 태그를 기반으로 아티클을 분리합니다.
     */
    private fun extractArticlesByHeadings(content: String): List<NewsletterArticle> {
        val articles = mutableListOf<NewsletterArticle>()
        val headingPattern = Regex("<h[1-6][^>]*>(.*?)</h[1-6]>", RegexOption.DOT_MATCHES_ALL)
        val matches = headingPattern.findAll(content)

        var currentTitle = ""
        var currentContent = ""
        var order = 1

        matches.forEach { match ->
            val title = match.groupValues[1].trim()
            val startIndex = match.range.first

            // 이전 아티클이 있으면 저장
            if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
                articles.add(NewsletterArticle(
                    title = currentTitle,
                    content = currentContent.trim(),
                    order = order++
                ))
            }

            currentTitle = title
            currentContent = content.substring(startIndex)
        }

        // 마지막 아티클 추가
        if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
            articles.add(NewsletterArticle(
                title = currentTitle,
                content = currentContent.trim(),
                order = order
            ))
        }

        // 아티클이 추출되지 않은 경우 기본 아티클 생성
        if (articles.isEmpty()) {
            articles.add(NewsletterArticle(
                title = "Main Article",
                content = content,
                order = 1
            ))
        }

        return articles
    }

    /**
     * 섹션 태그를 기반으로 아티클을 분리합니다.
     */
    private fun extractArticlesBySections(content: String): List<NewsletterArticle> {
        val articles = mutableListOf<NewsletterArticle>()
        val sectionPattern = Regex("<(div|section)[^>]*class=\"[^\"]*article[^\"]*\"[^>]*>(.*?)</(div|section)>", RegexOption.DOT_MATCHES_ALL)
        val matches = sectionPattern.findAll(content)

        matches.forEachIndexed { index, match ->
            val sectionContent = match.groupValues[2].trim()
            if (sectionContent.isNotEmpty()) {
                articles.add(NewsletterArticle(
                    title = "Article ${index + 1}",
                    content = sectionContent,
                    order = index + 1
                ))
            }
        }

        // 아티클이 추출되지 않은 경우 기본 아티클 생성
        if (articles.isEmpty()) {
            articles.add(NewsletterArticle(
                title = "Main Article",
                content = content,
                order = 1
            ))
        }

        return articles
    }

    /**
     * 구분자를 기반으로 아티클을 분리합니다.
     */
    private fun extractArticlesBySeparators(content: String): List<NewsletterArticle> {
        val articles = mutableListOf<NewsletterArticle>()
        val separators = listOf("---", "===", "***", "###", "___")

        var currentContent = content
        var order = 1

        separators.forEach { separator ->
            if (currentContent.contains(separator)) {
                val parts = currentContent.split(separator)
                parts.forEachIndexed { index, part ->
                    val trimmedPart = part.trim()
                    if (trimmedPart.isNotEmpty() && index > 0) { // 첫 번째 부분은 제외
                        articles.add(NewsletterArticle(
                            title = "Article $order",
                            content = trimmedPart,
                            order = order++
                        ))
                    }
                }
            }
        }

        // 아티클이 추출되지 않은 경우 기본 아티클 생성
        if (articles.isEmpty()) {
            articles.add(NewsletterArticle(
                title = "Main Article",
                content = content,
                order = 1
            ))
        }

        return articles
    }

    /**
     * 번호를 기반으로 아티클을 분리합니다.
     */
    private fun extractArticlesByNumbering(content: String): List<NewsletterArticle> {
        val articles = mutableListOf<NewsletterArticle>()
        val numberedPattern = Regex("(\\d+[.)]\\s+)(.*?)(?=\\d+[.)]\\s+|$)", RegexOption.DOT_MATCHES_ALL)
        val matches = numberedPattern.findAll(content)

        matches.forEachIndexed { index, match ->
            val number = match.groupValues[1].trim()
            val articleContent = match.groupValues[2].trim()

            if (articleContent.isNotEmpty()) {
                articles.add(NewsletterArticle(
                    title = "Article $number",
                    content = articleContent,
                    order = index + 1
                ))
            }
        }

        // 아티클이 추출되지 않은 경우 기본 아티클 생성
        if (articles.isEmpty()) {
            articles.add(NewsletterArticle(
                title = "Main Article",
                content = content,
                order = 1
            ))
        }

        return articles
    }

    private fun analyzeEmailStructure(emailMessage: EmailMessage) {
        logger.info("=== 메일 구조 분석 ===")
        logger.info("제목: ${emailMessage.subject}")
        logger.info("발신자: ${emailMessage.from}")
        logger.info("Content Type: ${emailMessage.contentType}")
        logger.info("HTML Content 존재: ${emailMessage.htmlContent != null}")
        logger.info("Text Content 존재: ${emailMessage.textContent != null}")
        logger.info("Extracted Content 길이: ${emailMessage.extractedContent.length}")
        logger.info("첨부파일 개수: ${emailMessage.attachments.size}")

        // HTML 콘텐츠가 있는 경우 구조 분석
        emailMessage.htmlContent?.let { html ->
            logger.info("HTML Content 길이: ${html.length}")
            logger.info("HTML에 링크 포함: ${html.contains("href=")}")
            logger.info("HTML에 이미지 포함: ${html.contains("<img")}")
            logger.info("HTML에 테이블 포함: ${html.contains("<table")}")
        }

        // 텍스트 콘텐츠가 있는 경우 구조 분석
        emailMessage.textContent?.let { text ->
            logger.info("Text Content 길이: ${text.length}")
            logger.info("텍스트에 URL 포함: ${text.contains("http")}")
            logger.info("텍스트에 이메일 포함: ${text.contains("@")}")
        }

        // 첨부파일 분석
        emailMessage.attachments.forEachIndexed { index, attachment ->
            logger.info("첨부파일 $index: ${attachment.fileName} (${attachment.contentType})")
        }

        logger.info("=== 분석 완료 ===")
    }

    private fun parseSenderInfo(sender: String): Pair<String, String> {
        val emailMatch = emailRegex.find(sender)

        return if (emailMatch != null) {
            val email = emailMatch.groupValues[1]
            val name = sender.substringBefore("<").trim()
            name to email
        } else {
            sender to sender
        }
    }

    private fun extractAndFormatContent(emailMessage: EmailMessage): FormattedContent {
        return when {
            // HTML 콘텐츠가 있는 경우 - 뉴스레터에 최적화된 정리
            emailMessage.htmlContent != null -> {
                val cleanedHtml = cleanHtmlContentForNewsletter(emailMessage.htmlContent)
                val fixedHtml = fixEncodingIssues(cleanedHtml)
                FormattedContent(
                    content = fixedHtml,
                    type = ContentType.HTML
                )
            }
            // 텍스트 콘텐츠가 있는 경우
            emailMessage.textContent != null -> {
                val cleanedText = cleanTextContentForNewsletter(emailMessage.textContent)
                val fixedText = fixEncodingIssues(cleanedText)
                FormattedContent(
                    content = fixedText,
                    type = ContentType.TEXT
                )
            }
            // 추출된 콘텐츠가 있는 경우
            emailMessage.extractedContent.isNotBlank() -> {
                val cleanedContent = cleanExtractedContentForNewsletter(emailMessage.extractedContent)
                val fixedContent = fixEncodingIssues(cleanedContent)
                FormattedContent(
                    content = fixedContent,
                    type = ContentType.MIXED
                )
            }
            else -> {
                FormattedContent(
                    content = "No readable content found",
                    type = ContentType.EMPTY
                )
            }
        }
    }

    /**
     * HTML 엔티티를 디코딩하고 불필요한 문자들을 제거합니다.
     */
    private fun decodeAndCleanHtmlEntities(text: String): String {
        return text
            // HTML 엔티티 디코딩
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#160;", " ")
            .replace("&#173;", "") // soft hyphen 제거
            .replace("&#8199;", "") // thin space 제거
            .replace("&#847;", "") // combining breve 제거
            .replace("&#8203;", "") // zero width space 제거
            .replace("&#8204;", "") // zero width non-joiner 제거
            .replace("&#8205;", "") // zero width joiner 제거
            .replace("&#8206;", "") // left-to-right mark 제거
            .replace("&#8207;", "") // right-to-left mark 제거
            .replace("&#65279;", "") // zero width no-break space 제거
            .replace("&#65533;", "") // replacement character 제거
            .replace("&#65532;", "") // object replacement character 제거
            // 유니코드 제어 문자 제거
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "")
            // 대체 문자 제거
            .replace(Regex("[\uFFFD]"), "")
            // 연속된 공백 정리
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 텍스트에서 불필요한 문자들을 제거합니다.
     */
    private fun removeUnwantedCharacters(text: String): String {
        return text
            // 제어 문자 제거
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "")
            // 대체 문자 제거
            .replace(Regex("[\uFFFD]"), "")
            // HTML 엔티티 정리
            .replace(Regex("&#\\d+;"), "")
            .replace(Regex("&[a-zA-Z]+;"), "")
            // 연속된 공백 정리
            .replace(Regex("\\s+"), " ")
            .trim()
    }

     fun cleanHtmlContentForNewsletter(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "") // 스크립트 제거
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "") // 스타일 제거
            .replace(Regex("<meta[^>]*>"), "") // 메타 태그 제거
            .replace(Regex("<link[^>]*>"), "") // 링크 태그 제거
            .replace(Regex("<head[^>]*>.*?</head>", RegexOption.DOT_MATCHES_ALL), "") // 헤드 제거
            .replace(Regex("<body[^>]*>"), "") // body 태그 시작 제거
            .replace(Regex("</body>"), "") // body 태그 끝 제거
            .replace(Regex("<html[^>]*>"), "") // html 태그 시작 제거
            .replace(Regex("</html>"), "") // html 태그 끝 제거
            .replace(Regex("<div[^>]*class=\"[^\"]*header[^\"]*\"[^>]*>.*?</div>", RegexOption.DOT_MATCHES_ALL), "") // 헤더 영역 제거
            .replace(Regex("<div[^>]*class=\"[^\"]*footer[^\"]*\"[^>]*>.*?</div>", RegexOption.DOT_MATCHES_ALL), "") // 푸터 영역 제거
            .replace(Regex("<div[^>]*class=\"[^\"]*nav[^\"]*\"[^>]*>.*?</div>", RegexOption.DOT_MATCHES_ALL), "") // 네비게이션 제거
            .replace(Regex("<a[^>]*href=\"[^\"]*unsubscribe[^\"]*\"[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL), "") // 구독해제 링크 제거
            .replace(Regex("<a[^>]*href=\"[^\"]*preferences[^\"]*\"[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL), "") // 설정 링크 제거
            .replace(Regex("<img[^>]*>"), "") // 이미지 제거
            .replace(Regex("<br\\s*/?>"), "\n") // br 태그를 줄바꿈으로 변환
            .replace(Regex("<p[^>]*>"), "\n") // p 태그 시작을 줄바꿈으로 변환
            .replace(Regex("</p>"), "\n") // p 태그 끝을 줄바꿈으로 변환
            .replace(Regex("<h[1-6][^>]*>"), "\n") // 헤딩 태그를 줄바꿈으로 변환
            .replace(Regex("</h[1-6]>"), "\n") // 헤딩 태그 끝을 줄바꿈으로 변환
            .replace(Regex("<[^>]*>"), "") // 나머지 HTML 태그 제거
            .let { decodeAndCleanHtmlEntities(it) } // HTML 엔티티 디코딩 및 정리
            .replace(Regex("\\n\\s*\\n"), "\n") // 연속된 줄바꿈 정리
            .replace(Regex("\\s+"), " ") // 연속된 공백 제거
            .trim()
    }

    private fun cleanTextContentForNewsletter(text: String): String {
        return text
            .let { removeUnwantedCharacters(it) } // 불필요한 문자 제거
            .replace(Regex("\\n\\s*\\n"), "\n") // 연속된 줄바꿈 제거
            .replace(Regex("\\s+"), " ") // 연속된 공백 제거
            .replace(Regex("(?i)unsubscribe"), "") // 구독해제 텍스트 제거
            .replace(Regex("(?i)preferences"), "") // 설정 텍스트 제거
            .trim()
    }

    private fun cleanExtractedContentForNewsletter(content: String): String {
        return content
            .let { removeUnwantedCharacters(it) } // 불필요한 문자 제거
            .replace(Regex("Plain Text: "), "")
            .replace(Regex("HTML: "), "")
            .replace(Regex("Attachment: [^\\n]*\\n"), "")
            .replace(Regex("(?i)unsubscribe"), "")
            .replace(Regex("(?i)preferences"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 인코딩 문제를 수정합니다.
     */
    private fun fixEncodingIssues(content: String): String {
        return content
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "") // 제어 문자 제거
            .replace(Regex("[\uFFFD]"), "?") // 대체 문자를 물음표로 변경
            .replace(Regex("&[a-zA-Z]+;"), " ") // HTML 엔티티 정리
            .replace(Regex("\\s+"), " ") // 연속된 공백 정리
            .trim()
    }

    /**
     * 다양한 인코딩으로 시도하여 텍스트를 복구합니다.
     */
    private fun tryMultipleEncodings(bytes: ByteArray): String {
        val encodings = listOf("UTF-8", "EUC-KR", "ISO-8859-1", "CP949", "Windows-1252")

        for (encoding in encodings) {
            try {
                val decoded = String(bytes, Charset.forName(encoding))
                if (isValidText(decoded)) {
                    logger.debug("Successfully decoded with $encoding")
                    return decoded
                }
            } catch (e: Exception) {
                logger.debug("Failed to decode with $encoding: ${e.message}")
            }
        }

        // 모든 인코딩이 실패하면 UTF-8로 강제 디코딩
        return String(bytes, Charset.forName("UTF-8"))
    }

    /**
     * 텍스트가 유효한지 확인합니다.
     */
    private fun isValidText(text: String): Boolean {
        // 너무 많은 제어 문자나 대체 문자가 있으면 유효하지 않음
        val controlChars = text.count { it.code in 0..31 || it.code in 127..159 }
        val replacementChars = text.count { it == '\uFFFD' }
        val totalChars = text.length

        if (totalChars == 0) return false

        val controlRatio = controlChars.toDouble() / totalChars
        val replacementRatio = replacementChars.toDouble() / totalChars

        return controlRatio < 0.1 && replacementRatio < 0.1
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NewsletterFormatter::class.java)
        private val emailRegex = Pattern.compile("<(.+?)>").toRegex()
    }
}

data class FormattedNewsletter(
    val subject: String,
    val sender: String,
    val senderEmail: String,
    val content: String,
    val contentType: String,
    val attachments: List<FormattedAttachment>,
    val receivedDate: java.time.LocalDateTime?,
    val originalContent: String,
    val articles: List<NewsletterArticle> = emptyList()
)

data class NewsletterArticle(
    val title: String,
    val content: String,
    val order: Int
)

data class FormattedContent(
    val content: String,
    val type: ContentType
)

data class FormattedAttachment(
    val filename: String,
    val contentType: String,
    val size: Long
)

enum class ContentType {
    HTML,
    TEXT,
    MIXED,
    EMPTY
}
