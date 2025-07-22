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
        
        return linkPattern.findAll(html).map { match ->
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
    fun extractByTag(html: String, tagName: String): List<String> {
        val pattern = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", RegexOption.DOT_MATCHES_ALL)
        
        return pattern.findAll(html).map { match ->
            extractText(match.groupValues[1])
        }.filter { it.isNotBlank() }.toList()
    }

    /**
     * HTML에서 제목 태그들(h1-h6) 추출
     */
    fun extractHeadings(html: String): List<Pair<Int, String>> {
        val headingPattern = Regex("""<h([1-6])[^>]*>(.*?)</h[1-6]>""", RegexOption.DOT_MATCHES_ALL)
        
        return headingPattern.findAll(html).map { match ->
            val level = match.groupValues[1].toInt()
            val text = extractText(match.groupValues[2])
            level to text
        }.filter { (_, text) -> text.isNotBlank() }.toList()
    }

    /**
     * HTML에서 리스트 아이템들 추출
     */
    fun extractListItems(html: String): List<String> {
        val listItemPattern = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
        
        return listItemPattern.findAll(html).map { match ->
            extractText(match.groupValues[1])
        }.filter { it.isNotBlank() }.toList()
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
        val entityMap = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&nbsp;" to " ",
            "&#39;" to "'",
            "&#x27;" to "'",
            "&#x2F;" to "/",
            "&#x3D;" to "=",
            "&#160;" to " ",
            "&hellip;" to "...",
            "&mdash;" to "—",
            "&ndash;" to "–",
            "&ldquo;" to """,
            "&rdquo;" to """,
            "&lsquo;" to "'",
            "&rsquo;" to "'"
        )
        
        var result = this
        entityMap.forEach { (entity, replacement) ->
            result = result.replace(entity, replacement)
        }
        
        // 숫자 형태의 HTML 엔티티 처리 (&#123; 형태)
        result = result.replace(Regex("""&#(\d+);""")) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull()
            if (code != null && code < 65536) {
                try {
                    code.toChar().toString()
                } catch (e: Exception) {
                    " "
                }
            } else " "
        }
        
        return result
    }

    /**
     * 공백 정규화
     */
    private fun String.normalizeWhitespace(): String {
        return this
            .replace(Regex("""\s+"""), " ")  // 연속된 공백을 하나로
            .replace(Regex("""\n\s*\n"""), "\n\n")  // 연속된 줄바꿈을 최대 2개로
            .trim()
    }

    /**
     * HTML인지 확인
     */
    fun isHtml(content: String): Boolean {
        val htmlIndicators = listOf(
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
     * HTML 내용에서 섹션별로 텍스트 분리
     */
    fun extractSections(html: String, sectionMarkers: List<String>): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        
        sectionMarkers.forEach { marker ->
            // 대소문자 구분 없이 섹션 마커 찾기
            val pattern = Regex("""(?i)<[^>]*>.*?${Regex.escape(marker)}.*?</[^>]*>""")
            val match = pattern.find(html)
            
            match?.let {
                // 해당 섹션부터 다음 주요 섹션까지의 내용 추출
                val startIndex = it.range.first
                var endIndex = html.length
                
                // 다른 섹션 마커들 중 가장 가까운 것 찾기
                sectionMarkers.filter { it != marker }.forEach { otherMarker ->
                    val otherPattern = Regex("""(?i)<[^>]*>.*?${Regex.escape(otherMarker)}.*?</[^>]*>""")
                    val otherMatch = otherPattern.find(html, startIndex + it.value.length)
                    otherMatch?.let { om ->
                        if (om.range.first < endIndex) {
                            endIndex = om.range.first
                        }
                    }
                }
                
                val sectionContent = html.substring(startIndex, endIndex)
                sections[marker] = extractText(sectionContent)
            }
        }
        
        return sections
    }
}
