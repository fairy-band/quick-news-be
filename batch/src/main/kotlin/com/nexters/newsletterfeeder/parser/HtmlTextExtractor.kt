package com.nexters.newsletterfeeder.parser

/**
 * HTML에서 텍스트를 추출하는 유틸리티
 * Jsoup 없이 정규식을 사용하여 HTML 태그 제거
 */
object HtmlTextExtractor {
    /**
     * HTML 태그를 제거하고 순수 텍스트만 추출
     */
    fun extractText(html: String): String {
        if (html.isBlank()) return ""

        return html
            .removeHtmlTags()
            .decodeHtmlEntities()
            .normalizeWhitespace()
            .trim()
    }

    /**
     * HTML에서 링크와 제목 추출
     * @return List of Pair(title, url)
     */
    fun extractLinks(html: String): List<Pair<String, String>> {
        val linkPattern = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        return linkPattern
            .findAll(html)
            .map { match ->
                val url = match.groupValues[1].trim()
                val title = extractText(match.groupValues[2]).trim()
                title to url
            }.filter { (title, url) ->
                title.isNotBlank() && url.isNotBlank() && url.startsWith("http")
            }.toList()
    }

    /**
     * HTML에서 특정 태그의 텍스트 추출
     */
    fun extractByTag(
        html: String,
        tagName: String
    ): List<String> {
        val pattern = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", RegexOption.DOT_MATCHES_ALL)

        return pattern
            .findAll(html)
            .map { match ->
                extractText(match.groupValues[1])
            }.filter { it.isNotBlank() }
            .toList()
    }

    /**
     * HTML에서 제목 태그들(h1-h6) 추출
     */
    fun extractHeadings(html: String): List<Pair<Int, String>> {
        val headingPattern = Regex("""<h([1-6])[^>]*>(.*?)</h[1-6]>""", RegexOption.DOT_MATCHES_ALL)

        return headingPattern
            .findAll(html)
            .map { match ->
                val level = match.groupValues[1].toInt()
                val text = extractText(match.groupValues[2])
                level to text
            }.filter { (_, text) -> text.isNotBlank() }
            .toList()
    }

    /**
     * HTML에서 리스트 아이템들 추출
     */
    fun extractListItems(html: String): List<String> {
        val listItemPattern = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)

        return listItemPattern
            .findAll(html)
            .map { match ->
                extractText(match.groupValues[1])
            }.filter { it.isNotBlank() }
            .toList()
    }

    /**
     * HTML인지 확인
     */
    fun isHtml(content: String): Boolean {
        val htmlIndicators =
            listOf(
                "<!DOCTYPE html",
                "<!doctype html",
                "<html",
                "<HTML",
                "<body",
                "<BODY"
            )

        return htmlIndicators.any { indicator ->
            content.contains(indicator, ignoreCase = true)
        }
    }

    /**
     * HTML 태그 제거
     */
    private fun String.removeHtmlTags(): String {
        // 스크립트와 스타일 태그 내용 완전 제거
        var result = this.replace(Regex("""<script[^>]*>.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")
        result = result.replace(Regex("""<style[^>]*>.*?</style>""", RegexOption.DOT_MATCHES_ALL), "")

        // 모든 HTML 태그 제거
        result = result.replace(Regex("""<[^>]+>"""), " ")

        return result
    }

    /**
     * HTML 엔티티 디코딩
     */
    private fun String.decodeHtmlEntities(): String {
        val entityMap =
            mapOf(
                "&amp;" to "&",
                "&lt;" to "<",
                "&gt;" to ">",
                "&quot;" to "\"",
                "&apos;" to "'",
                "&nbsp;" to " "
            )

        var result = this
        entityMap.forEach { (entity, replacement) ->
            result = result.replace(entity, replacement)
        }

        return result
    }

    /**
     * 공백 정규화
     */
    private fun String.normalizeWhitespace(): String =
        this
            .replace(Regex("""\s+"""), " ") // 연속된 공백을 하나로
            .trim()
}
