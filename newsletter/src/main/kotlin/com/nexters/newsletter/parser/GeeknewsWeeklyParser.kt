package com.nexters.newsletter.parser

import org.springframework.stereotype.Component

@Component
class GeeknewsWeeklyParser : MailParser {
    override fun isTarget(sender: String): Boolean = sender.contains("news@hada.io", ignoreCase = true)

    override fun parse(content: String): List<MailContent> {
        // 푸터 부분 제거 (✓ 사내 커뮤니케이션 도구... 이후 내용)
        val cleanedContent = content.substringBefore("✓ 사내 커뮤니케이션 도구")

        // 실제 구분선 패턴: 72개의 대시
        val separatorPattern = "-".repeat(72)
        val sections = cleanedContent.split(separatorPattern)

        val articles = mutableListOf<MailContent>()

        // 첫 번째 섹션은 인트로이므로 건너뛰고, 이후 섹션들을 2개씩 묶어서 처리
        // (제목 섹션, 설명 섹션) 쌍으로 처리
        var i = 1
        while (i < sections.size - 1) {
            val titleSection = sections[i].trim()
            val descriptionSection = sections[i + 1].trim()

            val article = parseArticleSection(titleSection, descriptionSection)
            if (article != null) {
                articles.add(article)
            }

            i += 2
        }

        return articles
    }

    private fun parseArticleSection(
        titleSection: String,
        descriptionSection: String
    ): MailContent? {
        // 제목 섹션에서 제목과 URL 추출
        // 형식: "제목   * URL"
        val titleParts = titleSection.split("*", limit = 2)
        if (titleParts.size < 2) return null

        val title = titleParts[0].trim()
        val url = titleParts[1].trim()

        if (title.isEmpty() || url.isEmpty()) return null

        // 설명 섹션 정리 (불필요한 공백 제거)
        val description =
            descriptionSection
                .replace(Regex("\\s+"), " ")
                .trim()

        return MailContent(
            title = title,
            content = description,
            link = url,
            section = "Article"
        )
    }

    companion object {
        private const val NEWSLETTER_MAIL_ADDRESS = "news@hada.io"
    }
}
