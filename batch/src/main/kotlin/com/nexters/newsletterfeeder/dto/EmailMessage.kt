package com.nexters.newsletterfeeder.dto

import jakarta.mail.BodyPart
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeUtility
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneId

data class AttachmentInfo(
    val fileName: String?,
    val contentType: String?,
    val size: Long?,
    val data: ByteArray?
) {
    // ByteArray는 equals()와 hashCode()를 재정의해야 함
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttachmentInfo

        if (fileName != other.fileName) return false
        if (contentType != other.contentType) return false
        if (size != other.size) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = fileName?.hashCode() ?: 0
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (size?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

data class ExtractedContent(
    val textContent: String?,
    val htmlContent: String?,
    val attachments: List<AttachmentInfo>
)

sealed class EmailMessage(
    val from: List<String>,
    val subject: String?,
    val receivedDate: LocalDateTime?,
    val sentDate: LocalDateTime?,
    val contentType: String?,
    // 이미 처리된 content
    val extractedContent: String,
    // 구조화된 content
    val textContent: String?,
    val htmlContent: String?,
    val attachments: List<AttachmentInfo>
) {
    data class StringContent(
        private val emailFrom: List<String>,
        private val emailSubject: String?,
        private val emailReceivedDate: LocalDateTime?,
        private val emailSentDate: LocalDateTime?,
        private val emailContentType: String?,
        private val emailExtractedContent: String,
        private val emailTextContent: String?,
        private val emailHtmlContent: String?,
        private val emailAttachments: List<AttachmentInfo>,
        val content: String
    ) : EmailMessage(
            emailFrom,
            emailSubject,
            emailReceivedDate,
            emailSentDate,
            emailContentType,
            emailExtractedContent,
            emailTextContent,
            emailHtmlContent,
            emailAttachments
        )

    data class MultipartContent(
        private val emailFrom: List<String>,
        private val emailSubject: String?,
        private val emailReceivedDate: LocalDateTime?,
        private val emailSentDate: LocalDateTime?,
        private val emailContentType: String?,
        private val emailExtractedContent: String,
        private val emailTextContent: String?,
        private val emailHtmlContent: String?,
        private val emailAttachments: List<AttachmentInfo>
    ) : EmailMessage(
            emailFrom,
            emailSubject,
            emailReceivedDate,
            emailSentDate,
            emailContentType,
            emailExtractedContent,
            emailTextContent,
            emailHtmlContent,
            emailAttachments
        )

    /**
     * InputStream 콘텐츠를 가진 이메일
     * - 바이너리 첨부파일 (이미지, 비디오, 오디오)
     * - 문서 파일 (PDF, DOC, XLS 등)
     * - 압축 파일 (ZIP, RAR, 7Z 등)
     * - 암호화된 콘텐츠
     * - 대용량 텍스트 데이터
     * - Base64 인코딩된 바이너리 데이터
     * - 특정 인코딩 처리가 필요한 콘텐츠
     */
    data class StreamContent(
        private val emailFrom: List<String>,
        private val emailSubject: String?,
        private val emailReceivedDate: LocalDateTime?,
        private val emailSentDate: LocalDateTime?,
        private val emailContentType: String?,
        private val emailExtractedContent: String,
        private val emailTextContent: String?,
        private val emailHtmlContent: String?,
        private val emailAttachments: List<AttachmentInfo>,
        val content: InputStream
    ) : EmailMessage(
            emailFrom,
            emailSubject,
            emailReceivedDate,
            emailSentDate,
            emailContentType,
            emailExtractedContent,
            emailTextContent,
            emailHtmlContent,
            emailAttachments
        )

    data class UnknownContent(
        private val emailFrom: List<String>,
        private val emailSubject: String?,
        private val emailReceivedDate: LocalDateTime?,
        private val emailSentDate: LocalDateTime?,
        private val emailContentType: String?,
        private val emailExtractedContent: String,
        private val emailTextContent: String?,
        private val emailHtmlContent: String?,
        private val emailAttachments: List<AttachmentInfo>,
        val content: Any?,
        val contentClassName: String?
    ) : EmailMessage(
            emailFrom,
            emailSubject,
            emailReceivedDate,
            emailSentDate,
            emailContentType,
            emailExtractedContent,
            emailTextContent,
            emailHtmlContent,
            emailAttachments
        )

    companion object {
        private val LOGGER = LoggerFactory.getLogger(EmailMessage::class.java)
        private val CHARSET_PATTERN = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)

        fun fromMimeMessage(mimeMessage: MimeMessage): EmailMessage {
            val from = mimeMessage.from?.map { it.toString() } ?: emptyList()
            val subject = mimeMessage.subject
            val receivedDate =
                mimeMessage.receivedDate
                    ?.toInstant()
                    ?.atZone(ZoneId.systemDefault())
                    ?.toLocalDateTime()
            val sentDate =
                mimeMessage.sentDate
                    ?.toInstant()
                    ?.atZone(ZoneId.systemDefault())
                    ?.toLocalDateTime()
            val contentType = mimeMessage.contentType

            return try {
                // MimeMessage를 독립적으로 복사하여 세션과 분리
                val copiedMessage = MimeMessage(mimeMessage)
                copiedMessage.saveChanges()

                when (val content = copiedMessage.content) {
                    is String -> {
                        val extractedContent = decodeText(content, contentType)
                        val (textContent, htmlContent) =
                            when {
                                contentType?.contains("text/html", ignoreCase = true) == true -> null to extractedContent
                                else -> extractedContent to null
                            }
                        StringContent(
                            from,
                            subject,
                            receivedDate,
                            sentDate,
                            contentType,
                            extractedContent,
                            textContent,
                            htmlContent,
                            emptyList(),
                            content
                        )
                    }
                    is Multipart -> {
                        val extractedContentResult = extractMultipartContent(content)
                        val extractedContent =
                            buildString {
                                extractedContentResult.textContent?.let { append("Plain Text: $it\n") }
                                extractedContentResult.htmlContent?.let { append("HTML: $it\n") }
                                extractedContentResult.attachments.forEach { append("Attachment: ${it.fileName ?: "unnamed"}\n") }
                            }
                        MultipartContent(
                            from,
                            subject,
                            receivedDate,
                            sentDate,
                            contentType,
                            extractedContent,
                            extractedContentResult.textContent,
                            extractedContentResult.htmlContent,
                            extractedContentResult.attachments
                        )
                    }
                    is InputStream -> {
                        val extractedContent = extractInputStreamContent(content, contentType)
                        // InputStream을 바이트 배열로 읽고 새로운 InputStream 생성
                        val bytes = content.readBytes()
                        val newInputStream = bytes.inputStream()
                        StreamContent(
                            from,
                            subject,
                            receivedDate,
                            sentDate,
                            contentType,
                            extractedContent,
                            extractedContent,
                            null,
                            emptyList(),
                            newInputStream
                        )
                    }
                    else -> {
                        val contentInfo = content?.toString()?.take(100) ?: "null"
                        val extractedContent =
                            "Unknown content type: ${content?.javaClass?.simpleName}, content: $contentInfo..."
                        UnknownContent(
                            from,
                            subject,
                            receivedDate,
                            sentDate,
                            contentType,
                            extractedContent,
                            null,
                            null,
                            emptyList(),
                            content,
                            content?.javaClass?.simpleName
                        )
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Error extracting content from MimeMessage", e)
                // content 추출 실패 시 UnknownContent로 처리
                val extractedContent = "Error extracting content: ${e.message}"
                UnknownContent(
                    from,
                    subject,
                    receivedDate,
                    sentDate,
                    contentType,
                    extractedContent,
                    null,
                    null,
                    emptyList(),
                    null,
                    "ContentExtractionFailed"
                )
            }
        }

        private fun extractInputStreamContent(
            inputStream: InputStream,
            contentType: String?
        ): String =
            try {
                val charset = getCharsetFromContentType(contentType)
                inputStream.bufferedReader(charset).use { it.readText() }
            } catch (e: Exception) {
                LOGGER.error("Error extracting InputStream content", e)
                "Error extracting content: ${e.message}"
            }

        private fun extractMultipartContent(multipart: Multipart): ExtractedContent {
            val textParts = mutableListOf<String>()
            val htmlParts = mutableListOf<String>()
            val attachments = mutableListOf<AttachmentInfo>()

            try {
                for (i in 0 until multipart.count) {
                    val part = multipart.getBodyPart(i)

                    try {
                        when {
                            part.isMimeType("text/plain") -> {
                                val content = extractTextContent(part)
                                textParts.add(content)
                            }
                            part.isMimeType("text/html") -> {
                                val content = extractTextContent(part)
                                htmlParts.add(content)
                            }
                            part.disposition != null && part.disposition.equals(Part.ATTACHMENT, ignoreCase = true) -> {
                                val attachmentInfo = extractAttachmentInfo(part)
                                attachments.add(attachmentInfo)
                            }
                            part.isMimeType("multipart/*") -> {
                                val nestedContent = part.content as? Multipart
                                if (nestedContent != null) {
                                    val nestedResult = extractMultipartContent(nestedContent)
                                    nestedResult.textContent?.let { textParts.add(it) }
                                    nestedResult.htmlContent?.let { htmlParts.add(it) }
                                    attachments.addAll(nestedResult.attachments)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LOGGER.warn("Error processing part $i: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Error extracting multipart content", e)
            }

            return ExtractedContent(
                textContent = textParts.joinToString("\n\n").takeIf { it.isNotBlank() },
                htmlContent = htmlParts.joinToString("\n\n").takeIf { it.isNotBlank() },
                attachments = attachments
            )
        }

        private fun extractTextContent(part: BodyPart): String =
            try {
                when (val content = part.content) {
                    is String -> {
                        decodeText(content, part.contentType)
                    }
                    is InputStream -> {
                        val charset = getCharsetFromContentType(part.contentType)
                        // InputStream을 바이트 배열로 읽고 새로운 InputStream으로 처리
                        val bytes = content.readBytes()
                        bytes.inputStream().bufferedReader(charset).use { it.readText() }
                    }
                    else -> content.toString()
                }
            } catch (e: Exception) {
                LOGGER.warn("Error extracting text content: ${e.message}")
                "Error extracting text content"
            }

        private fun decodeText(
            text: String,
            contentType: String?
        ): String {
            return try {
                val decodedText = MimeUtility.decodeText(text)
                val charset = getCharsetFromContentType(contentType)
                
                // HTML 엔티티 디코딩 및 정리
                val cleanedText = cleanHtmlEntities(decodedText)
                
                // 인코딩 문제가 있는지 확인
                if (hasEncodingIssues(cleanedText)) {
                    LOGGER.warn("Encoding issues detected in text, attempting to fix...")
                    return fixEncodingIssues(cleanedText)
                }
                
                cleanedText
            } catch (e: Exception) {
                LOGGER.warn("Error decoding text: ${e.message}")
                // 디코딩 실패 시 원본 텍스트 반환하되 인코딩 문제 수정
                fixEncodingIssues(cleanHtmlEntities(text))
            }
        }

        private fun getCharsetFromContentType(contentType: String?): Charset {
            return try {
                if (contentType != null) {
                    val matchResult = CHARSET_PATTERN.find(contentType)
                    if (matchResult != null) {
                        val charsetName = matchResult.groupValues[1].trim('"', '\'')
                        LOGGER.debug("Detected charset: $charsetName")
                        EmailCharset.getCharset(charsetName)
                    } else {
                        Charset.forName("UTF-8") // 기본값
                    }
                } else {
                    Charset.forName("UTF-8") // 기본값
                }
            } catch (e: Exception) {
                LOGGER.warn("Error parsing charset from content type: $contentType, using UTF-8")
                Charset.forName("UTF-8")
            }
        }

        private fun extractAttachmentInfo(part: BodyPart): AttachmentInfo {
            val fileName = part.fileName
            val contentType = part.contentType
            val size =
                try {
                    part.size.toLong()
                } catch (e: Exception) {
                    null
                }

            val data =
                try {
                    // 메모리 보호를 위해 50MB 제한
                    val maxSize = 50 * 1024 * 1024 // 50MB
                    if (size != null && size > maxSize) {
                        LOGGER.warn("Attachment '$fileName' is too large ($size bytes), skipping data extraction")
                        null
                    } else {
                        when (val content = part.content) {
                            is InputStream -> content.readBytes()
                            is ByteArray -> content
                            else -> {
                                LOGGER.warn("Unexpected attachment content type: ${content?.javaClass?.simpleName}")
                                null
                            }
                        }
                    }
                } catch (e: Exception) {
                    LOGGER.error("Error reading attachment data for '$fileName': ${e.message}")
                    null
                }

            return AttachmentInfo(
                fileName = fileName,
                contentType = contentType,
                size = size,
                data = data
            )
        }

        /**
         * 인코딩 문제가 있는지 확인합니다.
         */
        private fun hasEncodingIssues(text: String): Boolean {
            val controlChars = text.count { it.code in 0..31 || it.code in 127..159 }
            val replacementChars = text.count { it == '\uFFFD' }
            val totalChars = text.length

            if (totalChars == 0) return false

            val controlRatio = controlChars.toDouble() / totalChars
            val replacementRatio = replacementChars.toDouble() / totalChars

            return controlRatio > 0.05 || replacementRatio > 0.05
        }

        /**
         * 인코딩 문제를 수정합니다.
         */
        private fun fixEncodingIssues(text: String): String {
            return text
                .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "") // 제어 문자 제거
                .replace(Regex("[\uFFFD]"), "?") // 대체 문자를 물음표로 변경
                .replace(Regex("&[a-zA-Z]+;"), " ") // HTML 엔티티 정리
                .replace(Regex("\\s+"), " ") // 연속된 공백 정리
                .trim()
        }

        /**
         * HTML 엔티티를 정리합니다.
         */
        private fun cleanHtmlEntities(text: String): String {
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

        private fun cleanHtmlContentForNewsletter(html: String): String {
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
                .let { cleanHtmlEntities(it) } // HTML 엔티티 디코딩 및 정리
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
    }
}
