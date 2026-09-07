package com.nexters.external.support

object MarkdownValidator {
    private const val MIN_MARKDOWN_LENGTH = 300

    /**
     * AI 생성 마크다운 본문의 완결성과 필수 구조를 검증합니다.
     */
    fun isValid(markdown: String?): Boolean {
        if (markdown.isNullOrBlank()) return false
        val trimmed = markdown.trim()
        if (trimmed.length < MIN_MARKDOWN_LENGTH) return false

        // 1. 필수 헤더 구조 확인 (핵심 요약 및 본문 섹션)
        val hasSummary = trimmed.contains("## 💡 핵심 요약") || trimmed.contains("핵심 요약")
        val hasBody = trimmed.contains("## 📖 본문 상세 내용") || trimmed.contains("본문 상세 내용")
        if (!hasSummary || !hasBody) return false

        // 2. 완결성 확인: 결론 섹션이나 원문 링크가 존재하는지 확인
        val hasConclusion = trimmed.contains("## 🎯 결론") || trimmed.contains("결론 및 실무 시사점")
        val hasSourceLink = trimmed.contains("[원문") || trimmed.contains("원문 보기") || trimmed.contains("전체 보기")

        return hasConclusion || hasSourceLink
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
}
