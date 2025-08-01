package com.nexters.external.parser

import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import kotlin.collections.iterator

object TextSanitizer {
    // 보이지 않는 문자 목록
    private val invisibleChars =
        listOf(
            '\u200B', // Zero Width Space
            '\u200C', // Zero Width Non-Joiner
            '\u200D', // Zero Width Joiner
            '\u00A0', // Non-breaking space
            '\u2060', // Word Joiner
            '\u00AD', // Soft Hyphen
        )

    // HTML 엔티티 매핑
    private val htmlEntities =
        mapOf(
            "&nbsp;" to "\u00A0",
            "&lt;" to "<",
            "&gt;" to ">",
            "&amp;" to "&",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&cent;" to "¢",
            "&pound;" to "£",
            "&yen;" to "¥",
            "&euro;" to "€",
            "&copy;" to "©",
            "&reg;" to "®",
            "&trade;" to "™",
            "&mdash;" to "—",
            "&ndash;" to "–",
            "&lsquo;" to "'",
            "&rsquo;" to "'",
            "&ldquo;" to """,
            "&rdquo;" to """,
            "&bull;" to "•",
            "&hellip;" to "…",
            "=\n" to ""
        )

    // 숫자 엔티티 패턴 (&#123; 형식)
    private val numericEntityPattern = Pattern.compile("&#(\\d+);")

    // 16진수 엔티티 패턴 (&#x7B; 형식)
    private val hexEntityPattern = Pattern.compile("&#[xX]([0-9a-fA-F]+);")

    // 깨진 HTML 엔티티 패턴 (예: &n bsp; 또는 &n= bsp;)
    private val brokenEntityPattern =
        Pattern.compile(
            "&([a-zA-Z]+)[\\s=]+(bsp|lt|gt|amp|quot|apos|cent|pound|yen|euro|copy|reg|trade|mdash|ndash|lsquo|rsquo|ldquo|rdquo|bull|hellip);"
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

    // HTML 엔티티 디코딩 함수
    fun decodeHtmlEntities(input: String): String {
        var result = input

        // 깨진 HTML 엔티티 처리 (예: &n= bsp;)
        val brokenMatcher = brokenEntityPattern.matcher(result)
        val brokenBuffer = StringBuilder()

        while (brokenMatcher.find()) {
            val prefix = brokenMatcher.group(1)
            val suffix = brokenMatcher.group(2)
            val fullEntity = "&$prefix$suffix;"

            // 수정된 엔티티가 유효한 엔티티인지 확인
            val replacement = htmlEntities[fullEntity] ?: fullEntity
            brokenMatcher.appendReplacement(brokenBuffer, replacement)
        }
        brokenMatcher.appendTail(brokenBuffer)
        result = brokenBuffer.toString()

        // 명명된 엔티티 처리 (예: &nbsp;)
        for ((entity, replacement) in htmlEntities) {
            result = result.replace(entity, replacement)
        }

        // 숫자 엔티티 처리 (예: &#123;)
        val numericMatcher = numericEntityPattern.matcher(result)
        val numericBuffer = StringBuilder()

        while (numericMatcher.find()) {
            val codePoint = numericMatcher.group(1).toInt()
            numericMatcher.appendReplacement(numericBuffer, codePoint.toChar().toString())
        }
        numericMatcher.appendTail(numericBuffer)
        result = numericBuffer.toString()

        // 16진수 엔티티 처리 (예: &#x7B;)
        val hexMatcher = hexEntityPattern.matcher(result)
        val hexBuffer = StringBuilder()

        while (hexMatcher.find()) {
            val codePoint = hexMatcher.group(1).toInt(16)
            hexMatcher.appendReplacement(hexBuffer, codePoint.toChar().toString())
        }
        hexMatcher.appendTail(hexBuffer)
        result = hexBuffer.toString()

        return result
    }

    // 모든 공백 문자 제거 (HTML 엔티티 복구를 위한 전처리)
    fun normalizeHtmlEntities(input: String): String {
        // HTML 엔티티 내부의 공백 제거 (예: &n bsp; -> &nbsp;)
        var result = input

        // &로 시작하고 ;로 끝나는 패턴 찾기
        val pattern = Pattern.compile("&([^;]*);")
        val matcher = pattern.matcher(result)
        val buffer = StringBuilder()

        while (matcher.find()) {
            val entity = matcher.group(1)
            // 엔티티 내부의 공백 및 = 제거
            val normalizedEntity = "&${entity.replace(Regex("[\\s=]+"), "")};"
            matcher.appendReplacement(buffer, normalizedEntity)
        }
        matcher.appendTail(buffer)

        return buffer.toString()
    }

    // 보이지 않는 문자 제거 함수
    fun removeInvisibleCharacters(input: String): String = input.filterNot { it in invisibleChars }

    // 디코딩 + 정리 종합 처리 함수
    fun decodeAndSanitize(input: String): String {
        // 순서대로 처리:
        // 1. Quoted-Printable 디코딩
        // 2. HTML 엔티티 정규화 (깨진 엔티티 수정)
        // 3. HTML 엔티티 디코딩
        // 4. 보이지 않는 문자 제거
        val decoded = decodeQuotedPrintable(input)
        val normalized = normalizeHtmlEntities(decoded)
        val htmlDecoded = decodeHtmlEntities(normalized)
        return removeInvisibleCharacters(htmlDecoded)
    }
}
