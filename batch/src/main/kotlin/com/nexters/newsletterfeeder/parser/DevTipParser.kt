package com.nexters.newsletterfeeder.parser

class DevTipParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true) ||
            sender.contains("JavaScript Weekly", ignoreCase = true) ||
            sender.contains("javascriptweekly.com", ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        val multipartEntries = parseMultipartEntries(content)
        return multipartEntries.flatMap { entry ->
            parseDevTipEntry(entry)
        }
    }

    private fun parseMultipartEntries(content: String): List<MultipartEntry> {
        val entries = mutableListOf<MultipartEntry>()

        // MultipartContent로 시작하는 부분들을 찾기
        val startPattern = "MultipartContent("
        var startIndex = 0

        while (true) {
            val foundIndex = content.indexOf(startPattern, startIndex)
            if (foundIndex == -1) break

            // 괄호 매칭으로 끝 위치 찾기
            var parenCount = 0
            var endIndex = foundIndex + startPattern.length
            var foundClosing = false

            for (i in endIndex until content.length) {
                when (content[i]) {
                    '(' -> parenCount++
                    ')' -> {
                        if (parenCount == 0) {
                            endIndex = i
                            foundClosing = true
                            break
                        } else {
                            parenCount--
                        }
                    }
                }
            }

            if (foundClosing) {
                val entryContent = content.substring(foundIndex + startPattern.length, endIndex)
                val entry = parseMultipartContent(entryContent)
                if (entry != null) {
                    entries.add(entry)
                }
            }

            startIndex = endIndex + 1
        }

        return entries
    }

    private fun parseMultipartContent(content: String): MultipartEntry? {
        try {
            // emailFrom 추출
            val emailFromStart = content.indexOf("emailFrom=[")
            if (emailFromStart == -1) return null
            val emailFromEnd = content.indexOf("]", emailFromStart)
            if (emailFromEnd == -1) return null
            val emailFrom = content.substring(emailFromStart + 11, emailFromEnd).trim('"')

            // emailSubject 추출
            val subjectStart = content.indexOf("emailSubject=")
            if (subjectStart == -1) return null
            val subjectEnd = content.indexOf(", emailReceivedDate", subjectStart)
            if (subjectEnd == -1) return null
            val emailSubject = content.substring(subjectStart + 13, subjectEnd).trim()

            // emailSentDate 추출
            val sentDateStart = content.indexOf("emailSentDate=")
            if (sentDateStart == -1) return null
            val sentDateEnd = content.indexOf(", emailContentType", sentDateStart)
            if (sentDateEnd == -1) return null
            val emailSentDate = content.substring(sentDateStart + 14, sentDateEnd).trim()

            // emailExtractedContent 추출
            val extractedContentStart = content.indexOf("emailExtractedContent=")
            if (extractedContentStart == -1) return null
            var emailExtractedContent = content.substring(extractedContentStart + 22).trim()

            // "Plain Text: " 접두사 제거
            if (emailExtractedContent.startsWith("Plain Text: ")) {
                emailExtractedContent = emailExtractedContent.substring(12)
            }

            return MultipartEntry(
                emailFrom = emailFrom,
                emailSubject = emailSubject,
                emailSentDate = emailSentDate,
                emailExtractedContent = emailExtractedContent
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseDevTipEntry(entry: MultipartEntry): List<MailContent> {
        val result = mutableListOf<MailContent>()

        if (!isTarget(entry.emailFrom)) {
            return emptyList()
        }

        when {
            entry.emailSubject.contains("Dev Tip", ignoreCase = true) -> {
                result.addAll(parseDevTip(entry))
            }
            entry.emailSubject.contains("JavaScript Weekly", ignoreCase = true) -> {
                result.addAll(parseJavaScriptWeekly(entry))
            }
        }

        return result
    }

    private fun parseDevTip(entry: MultipartEntry): List<MailContent> {
        val content = entry.emailExtractedContent
        val tipNumber = extractTipNumber(entry.emailSubject)
        val tipTitle = extractTipTitle(entry.emailSubject)

        // Dev Tip의 경우 전체 내용을 하나의 컨텐츠로 처리
        return listOf(
            MailContent(
                title = tipTitle,
                content = "[$tipNumber] $content",
                link = "", // Dev Tip은 보통 링크가 없음
                section = "Dev Tip"
            )
        )
    }

    private fun parseJavaScriptWeekly(entry: MultipartEntry): List<MailContent> {
        val content = entry.emailExtractedContent
        val articles = mutableListOf<MailContent>()
        val lines = content.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // → 로 시작하는 제목 라인 찾기
            if (line.startsWith("→ ")) {
                val title = line.substring(2).trim()

                // 다음 줄들에서 Link: 찾기
                var linkLine = ""
                for (j in i + 1 until lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.startsWith("Link: ")) {
                        linkLine = nextLine.substring(6).trim()
                        break
                    }
                    // 다음 → 라인을 만나면 중단
                    if (nextLine.startsWith("→ ")) {
                        break
                    }
                }

                if (linkLine.isNotEmpty() && linkLine.startsWith("http")) {
                    // 제목과 링크 사이의 설명 수집
                    val descriptionLines = mutableListOf<String>()
                    for (j in i + 1 until lines.size) {
                        val nextLine = lines[j].trim()
                        if (nextLine.startsWith("Link:") || nextLine.startsWith("→ ")) {
                            break
                        }
                        if (nextLine.isNotEmpty()) {
                            descriptionLines.add(nextLine)
                        }
                    }

                    val description = descriptionLines.joinToString(" ").ifEmpty { title }

                    articles.add(
                        MailContent(
                            title = title,
                            content = "[JavaScript Weekly] $description",
                            link = linkLine,
                            section = "JavaScript Weekly"
                        )
                    )
                }
            }
            i++
        }

        return articles
    }

    private fun extractTipNumber(subject: String): String {
        val hashIndex = subject.indexOf('#')
        if (hashIndex != -1) {
            val colonIndex = subject.indexOf(':', hashIndex)
            if (colonIndex != -1) {
                val numberPart = subject.substring(hashIndex + 1, colonIndex).trim()
                if (numberPart.all { it.isDigit() }) {
                    return "Dev Tip #$numberPart"
                }
            }
        }
        return "Dev Tip"
    }

    private fun extractTipTitle(subject: String): String {
        // "Dev Tip #366: Generative AI is the New Offshoring" -> "Generative AI is the New Offshoring"
        val colonIndex = subject.indexOf(':')
        return if (colonIndex != -1) {
            subject.substring(colonIndex + 1).trim()
        } else {
            subject.removePrefix("Dev Tip").trim()
        }
    }

    private fun extractDescription(
        fullMatch: String,
        title: String
    ): String {
        val lines = fullMatch.lines()
        val description =
            lines
                .drop(1) // 첫 번째 줄(제목) 제외
                .takeWhile { !it.startsWith("Link:") } // Link: 라인 전까지
                .joinToString(" ")
                .trim()

        return description.ifEmpty { title }
    }

    private data class MultipartEntry(
        val emailFrom: String,
        val emailSubject: String,
        val emailSentDate: String,
        val emailExtractedContent: String
    )

    companion object {
        private const val NEWSLETTER_NAME = "Ardalis"
        private const val NEWSLETTER_MAIL_ADDRESS = "ardalis.com"
    }
}
