package com.nexters.external.filter

import com.nexters.external.entity.Content
import com.nexters.external.enums.ContentProviderType
import org.slf4j.LoggerFactory

/**
 * 여러 필터를 체인으로 연결하여 실행
 */
class FilterChain(
    private val filters: List<ContentFilter>
) {
    private val logger = LoggerFactory.getLogger(FilterChain::class.java)

    /**
     * 단일 content에 대해 모든 필터를 순차적으로 실행
     */
    fun filter(content: Content): FilterChainResult {
        val results = mutableMapOf<String, FilterResult>()

        for (filter in filters) {
            val result = filter.filter(content)
            results[filter.getName()] = result

            when (result) {
                is FilterResult.Pass -> continue
                is FilterResult.Fail -> {
                    logger.info(
                        "Content ID ${content.id} filtered out by ${filter.getName()}: ${result.reason}"
                    )
                    return FilterChainResult.Failed(results, filter.getName(), result.reason)
                }
            }
        }

        return FilterChainResult.Passed(results)
    }

    /**
     * 여러 content를 필터링하여 통과한 것만 반환
     */
    fun filter(contents: List<Content>): List<Content> =
        contents.filter { content ->
            filter(content) is FilterChainResult.Passed
        }

    companion object {
        /**
         * 빌더 패턴으로 FilterChain 생성
         */
        fun builder() = Builder()
    }

    class Builder {
        private val filters = mutableListOf<ContentFilter>()

        fun addFilter(filter: ContentFilter) =
            apply {
                filters.add(filter)
            }

        fun addLengthFilter(
            minLength: Int,
            maxLength: Int
        ) = apply {
            filters.add(LengthFilter(minLength, maxLength))
        }

        fun addProviderTypeFilter(vararg types: ContentProviderType) =
            apply {
                filters.add(ProviderTypeFilter(types.toSet()))
            }

        fun addDateFilter(withinDays: Int) =
            apply {
                filters.add(DateFilter(withinDays))
            }

        fun build() = FilterChain(filters.toList())
    }
}

/**
 * 필터 체인 실행 결과
 */
sealed interface FilterChainResult {
    val filterResults: Map<String, FilterResult>

    /**
     * 모든 필터 통과
     */
    data class Passed(
        override val filterResults: Map<String, FilterResult>
    ) : FilterChainResult

    /**
     * 필터 실패
     */
    data class Failed(
        override val filterResults: Map<String, FilterResult>,
        val failedFilterName: String,
        val reason: String
    ) : FilterChainResult
}
