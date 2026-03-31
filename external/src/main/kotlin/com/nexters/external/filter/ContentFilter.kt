package com.nexters.external.filter

import com.nexters.external.entity.Content

/**
 * Content가 ExposureContent 생성 대상인지 판단하는 필터 인터페이스
 */
interface ContentFilter {
    /**
     * Content가 조건을 만족하는지 확인
     * @return FilterResult 필터 통과 여부와 실패 사유
     */
    fun filter(content: Content): FilterResult

    /**
     * 필터의 이름 (로깅용)
     */
    fun getName(): String
}

/**
 * 필터 결과
 */
sealed interface FilterResult {
    val passed: Boolean

    /**
     * 필터 통과
     */
    data object Pass : FilterResult {
        override val passed: Boolean = true
    }

    /**
     * 필터 실패
     */
    data class Fail(
        val reason: String
    ) : FilterResult {
        override val passed: Boolean = false
    }
}
