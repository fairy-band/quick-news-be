package com.nexters.newsletterfeeder.parser

import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream

class AndroidWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean =
        sender.contains(NEWSLETTER_NAME, ignoreCase = true) ||
            sender.contains(NEWSLETTER_MAIL_ADDRESS, ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        // 1) HTML -> Jsoup Document
        val doc = Jsoup.parse(content)

        // 스타일 / 스크립트 제거 – 실제 내용 파싱에 불필요한 태그 제거
        doc.select("style, script").remove()

        // 2) 이슈 번호 / 날짜 추출 – 이메일 전체 텍스트에서 정규식으로 추출
        val fullText = doc.text()
        val issueNumber = ISSUE_NUMBER_REGEX.find(fullText)?.groupValues?.getOrNull(1) ?: "Unknown"
        val issueDate = ISSUE_DATE_REGEX.find(fullText)?.value ?: "Unknown date"

        // 3) 테이블을 순회하며 섹션 → 아티클 추출
        var currentSection: Section? = null
        val seenTitles = mutableSetOf<String>()
        val results = mutableListOf<MailContent>()

        for (table in doc.select("table")) {
            val tableText = TextSanitizer.decodeAndSanitize(table.text().trim())

            // 섹션 헤더인지 확인
            val sectionCandidate = Section.fromLabel(tableText)
            if (sectionCandidate != null) {
                currentSection = sectionCandidate
                continue
            }

            // 본문 아티클 추출 – 이미지/아이콘이 섞여 있을 수 있으므로 첫 번째 a[href] 기준으로 추출
            val anchor = table.selectFirst("a[href]") ?: continue
            val title = TextSanitizer.decodeAndSanitize(anchor.text().trim())

            // 노이즈 제거 – 빈 제목 혹은 블랙리스트에 포함된 경우 skip
            if (title.isBlank() || title in IGNORED_TITLES || !seenTitles.add(title)) continue

            val link = anchor.attr("href").cleanUrl()

            // 설명은 테이블 텍스트에서 제목 이후 부분을 사용
            val description = tableText
                .substringAfter(title, "")
                .trim()
                .takeIf { it.isNotBlank() }
                ?: ""

            val sectionLabel = currentSection?.label ?: "Unknown"

            val contentText = "[${sectionLabel}] Issue #${issueNumber} (${issueDate}): ${description}"

            results += MailContent(
                title = title,
                content = contentText,
                link = link,
                section = sectionLabel
            )
        }

        return results
    }

    private fun String.cleanUrl(): String =
        replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .replace("=3D", "=")
            .replace("=", "")

    private fun String.normalizeSoftBreaks(): String =
        replace("=\r\n", "")
            .replace("=\n", "")
            .replace("=\r", "")

    companion object {
        private val ISSUE_NUMBER_REGEX = Regex("ISSUE #(\\d+)", RegexOption.IGNORE_CASE)
        private val ISSUE_DATE_REGEX = Regex("(\\d+)[a-z]{0,2}\\s+[A-Za-z]+\\s+\\d{4}", RegexOption.IGNORE_CASE)

        private const val NEWSLETTER_NAME = "Android Weekly"
        private const val NEWSLETTER_MAIL_ADDRESS = "androidweekly.net"

        // 노이즈 제거용 블랙리스트 제목
        private val IGNORED_TITLES = setOf(
            "View in w= eb browser",
            "POST A JOB",
            "SPONSORED POST",
            "PATREON",
            "MERCHANDISE",
            "TWIT TER",
            "Unsubscribe newsletter.feeding@gmail.com",
            "Update Profile",
            "Our Privacy Policy",
            "Constant Contac t Data Notice",
            "contact@androidweekly.net",
            "Try email marketing for free today!",
            "SPONSORED POST=",
            "TWIT= TER",
        )
    }

    private enum class Section(
        val label: String,
    ) {
        ARTICLES("Articles & Tutorials"),
        JOBS("Jobs"),
        LIBRARIES("Libraries & Code"),
        NEWS("News"),
        VIDEOS("Videos & Podcasts"),
        ;

        companion object {
            fun fromLabel(label: String) = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
        }
    }

    object TextSanitizer {

        // 보이지 않는 문자 목록
        private val invisibleChars = listOf(
            '\u200B', // Zero Width Space
            '\u200C', // Zero Width Non-Joiner
            '\u200D', // Zero Width Joiner
            '\u00A0', // Non-breaking space
            '\u2060', // Word Joiner
            '\u00AD',  // Soft Hyphen
        )

        // Quoted-Printable 디코딩 함수
        fun decodeQuotedPrintable(input: String): String {
            val output = ByteArrayOutputStream()
            var i = 0
            while (i < input.length) {
                if (input[i] == '=' && i + 2 < input.length) {
                    val hex = input.substring(i + 1, i + 3)
                    try {
                        output.write(hex.toInt(16))
                        i += 3
                    } catch (e: NumberFormatException) {
                        // 잘못된 형식이면 그냥 문자 그대로 출력
                        output.write(input[i].code)
                        i++
                    }
                } else {
                    output.write(input[i].code)
                    i++
                }
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        }

        // 보이지 않는 문자 제거 함수
        fun removeInvisibleCharacters(input: String): String {
            return input.filterNot { it in invisibleChars }
        }

        // 디코딩 + 정리 종합 처리 함수
        fun decodeAndSanitize(input: String): String {
            val decoded = decodeQuotedPrintable(input)
            return removeInvisibleCharacters(decoded)
        }
    }
}
