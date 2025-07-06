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
                mimeMessage.receivedDate?.toInstant()
                    ?.atZone(ZoneId.systemDefault())
                    ?.toLocalDateTime()
            val sentDate =
                mimeMessage.sentDate?.toInstant()
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
                            from, subject, receivedDate, sentDate, contentType, extractedContent,
                            textContent, htmlContent, emptyList(), content
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
                            from, subject, receivedDate, sentDate, contentType, extractedContent,
                            extractedContentResult.textContent, extractedContentResult.htmlContent, extractedContentResult.attachments
                        )
                    }
                    is InputStream -> {
                        val extractedContent = extractInputStreamContent(content, contentType)
                        // InputStream을 바이트 배열로 읽고 새로운 InputStream 생성
                        val bytes = content.readBytes()
                        val newInputStream = bytes.inputStream()
                        StreamContent(
                            from, subject, receivedDate, sentDate, contentType, extractedContent,
                            extractedContent, null, emptyList(), newInputStream
                        )
                    }
                    else -> {
                        val contentInfo = content?.toString()?.take(100) ?: "null"
                        val extractedContent =
                            "Unknown content type: ${content?.javaClass?.simpleName}, content: $contentInfo..."
                        UnknownContent(
                            from, subject, receivedDate, sentDate, contentType, extractedContent,
                            null, null, emptyList(), content, content?.javaClass?.simpleName
                        )
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Error extracting content from MimeMessage", e)
                // content 추출 실패 시 UnknownContent로 처리
                val extractedContent = "Error extracting content: ${e.message}"
                UnknownContent(
                    from, subject, receivedDate, sentDate, contentType, extractedContent,
                    null, null, emptyList(), null, "ContentExtractionFailed"
                )
            }
        }

        private fun extractInputStreamContent(
            inputStream: InputStream,
            contentType: String?
        ): String {
            return try {
                val charset = getCharsetFromContentType(contentType)
                inputStream.bufferedReader(charset).use { it.readText() }
            } catch (e: Exception) {
                LOGGER.error("Error extracting InputStream content", e)
                "Error extracting content: ${e.message}"
            }
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

        private fun extractTextContent(part: BodyPart): String {
            return try {
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
        }

        private fun decodeText(
            text: String,
            contentType: String?
        ): String {
            return try {
                val decodedText = MimeUtility.decodeText(text)

                val charset = getCharsetFromContentType(contentType)
                if (charset != Charset.defaultCharset()) {
                    decodedText
                } else {
                    decodedText
                }
            } catch (e: Exception) {
                LOGGER.warn("Error decoding text: ${e.message}")
                text
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
    }
}
