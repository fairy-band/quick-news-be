package com.nexters.external.support

object MarkdownValidator {
    private const val MIN_MARKDOWN_LENGTH = 300
    private const val CHARACTERS_PER_MINUTE = 450

    /**
     * AI 생성 마크다운 본문의 완결성과 필수 구조를 검증합니다.
     */
    fun isValid(markdown: String?): Boolean {
        if (markdown.isNullOrBlank()) return false
        val trimmed = markdown.trim()
        if (trimmed.length < MIN_MARKDOWN_LENGTH) return false

        // 1. 필수 헤더 구조 확인 (핵심 요약 및 본문 섹션)
        val hasSummary =
            trimmed.contains("## ⚡ 3줄 핵심 요약") ||
                trimmed.contains("## 💡 핵심 요약") ||
                trimmed.contains("핵심 요약")
        val hasBody = trimmed.contains("## 📖 본문 상세 내용") || trimmed.contains("본문 상세 내용")
        if (!hasSummary || !hasBody) return false

        // 2. 완결성 확인: 결론/실무 적용 섹션이나 원문 링크가 존재하는지 확인
        val hasConclusion =
            trimmed.contains("## 🎯 결론") ||
                trimmed.contains("결론 및 시사점") ||
                trimmed.contains("결론 및 실무 시사점") ||
                trimmed.contains("## 💡 실무 적용 포인트")
        val hasSourceLink = trimmed.contains("[원문") || trimmed.contains("원문 보기") || trimmed.contains("전체 보기")

        return hasConclusion || hasSourceLink
    }

    /**
     * 마크다운 본문의 길이를 기반으로 예상 완독 시간을 계산합니다.
     */
    fun estimateReadingTime(markdown: String): String {
        val plainTextLength = markdown
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?\\n```"), " ")
            .replace(Regex("[#>*`\\[\\]\\(\\)-]"), " ")
            .trim()
            .length

        val minutes = plainTextLength / CHARACTERS_PER_MINUTE
        val remainderSeconds = ((plainTextLength % CHARACTERS_PER_MINUTE) * 60 / CHARACTERS_PER_MINUTE) / 10 * 10

        return when {
            minutes == 0 -> "약 1분 이내"
            remainderSeconds in 10..50 -> "약 ${minutes}분 ${remainderSeconds}초"
            else -> "약 ${minutes.coerceAtLeast(1)}분"
        }
    }

    /**
     * 마크다운 본문의 길이를 기반으로 예상 완독 시간을 정수(분 단위)로 계산합니다. (최소 1분)
     * 마크다운 본문에 이미 예상 완독 시간 표기가 존재하는 경우 해당 숫자를 우선 추출합니다.
     */
    fun estimateReadingTimeMinutes(markdown: String?): Int {
        if (markdown.isNullOrBlank()) return 1

        val match = Regex("""예상 완독 시간[^\d]*(\d+)분""").find(markdown)
        if (match != null) {
            val parsed = match.groupValues[1].toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }

        val plainTextLength = markdown
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?\\n```"), " ")
            .replace(Regex("[#>*`\\[\\]\\(\\)-]"), " ")
            .trim()
            .length

        return maxOf(1, (plainTextLength + 225) / CHARACTERS_PER_MINUTE)
    }

    /**
     * 마크다운 본문에 원문 링크가 누락된 경우, 안전하게 하단에 원문 링크를 보강합니다.
     */
    fun ensureSourceLink(markdown: String, originalUrl: String): String {
        val trimmed = markdown.trim()
        if (trimmed.contains("[원문") || trimmed.contains("원문 보기") || trimmed.contains("전체 보기")) {
            return trimmed
        }
        if (originalUrl.isBlank()) {
            return trimmed
        }
        return "$trimmed\n\n---\n👉 [원문 아티클 전체 보기]($originalUrl)"
    }

    /**
     * 마크다운에 예상 완독 시간 및 원문 링크가 보장되도록 표준화합니다.
     * 기존에 저장된 레거시 마크다운도 사용자 서빙 시 30초 스캔 규격으로 자동 보정됩니다.
     */
    fun standardizeMarkdown(markdown: String, originalUrl: String): String {
        var result = ensureSourceLink(markdown, originalUrl)

        // 예상 완독 시간이 이미 존재하면 그대로 반환
        if (result.contains("예상 완독 시간") || result.contains("⏱️")) {
            return result
        }

        val readingTime = estimateReadingTime(result)
        val readingTimeCallout = "> ⏱️ **예상 완독 시간**: $readingTime\n\n"

        // # 제목 바로 아래에 완독 시간 콜아웃 주입
        val titleMatch = Regex("^(#\\s+[^\n]+)", RegexOption.MULTILINE).find(result)
        return if (titleMatch != null) {
            val titleEnd = titleMatch.range.last + 1
            result.substring(0, titleEnd) + "\n\n" + readingTimeCallout + result.substring(titleEnd).trimStart()
        } else {
            readingTimeCallout + result
        }
    }
}
