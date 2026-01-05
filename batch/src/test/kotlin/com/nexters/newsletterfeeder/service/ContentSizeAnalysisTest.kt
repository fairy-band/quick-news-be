package com.nexters.newsletterfeeder.service

import com.nexters.external.entity.ContentProvider
import com.nexters.external.enums.ContentProviderType
import com.nexters.external.repository.ContentRepository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

/**
 * 실제 블로그 컨텐츠의 크기를 분석하여 적절한 토큰 제한을 결정하기 위한 테스트
 *
 * 실행 방법: @Disabled 어노테이션을 제거하고 로컬 DB가 실행 중인 상태에서 실행
 */
@SpringBootTest
// @Disabled("Manual execution only - requires database connection")
class ContentSizeAnalysisTest {
    @Autowired
    private lateinit var contentRepository: ContentRepository

    private val logger = LoggerFactory.getLogger(ContentSizeAnalysisTest::class.java)

    @Test
    fun `analyze blog content sizes to determine appropriate token limits`() {
        // BLOG 타입의 컨텐츠를 샘플링 (최대 100개)
        val sampleSize = 100
        val pageable = PageRequest.of(0, sampleSize)

        logger.info("Fetching up to $sampleSize BLOG contents for analysis...")

        val blogContents =
            contentRepository
                .findAll(pageable)
                .content
                .filter { it.contentProvider?.type == ContentProviderType.BLOG }

        if (blogContents.isEmpty()) {
            logger.warn("No BLOG contents found in database")
            return
        }

        logger.info("Analyzing ${blogContents.size} BLOG contents...")

        // 컨텐츠 길이 통계 수집
        val contentLengths = blogContents.map { it.content.length }
        val sortedLengths = contentLengths.sorted()

        // 통계 계산
        val min = sortedLengths.first()
        val max = sortedLengths.last()
        val average = sortedLengths.average()
        val median = sortedLengths[sortedLengths.size / 2]
        val p90 = sortedLengths[(sortedLengths.size * 0.90).toInt()]
        val p95 = sortedLengths[(sortedLengths.size * 0.95).toInt()]
        val p99 = sortedLengths[(sortedLengths.size * 0.99).toInt()]

        // 길이별 분포
        val ranges =
            listOf(
                0 to 5_000,
                5_000 to 10_000,
                10_000 to 20_000,
                20_000 to 50_000,
                50_000 to 100_000,
                100_000 to Int.MAX_VALUE
            )

        val distribution =
            ranges.map { (start, end) ->
                val count = contentLengths.count { it in start until end }
                val percentage = (count.toDouble() / contentLengths.size * 100).toInt()
                "$start-${if (end == Int.MAX_VALUE) "∞" else end}: $count ($percentage%)"
            }

        // 결과 출력
        logger.info(
            """
            ============================================
            BLOG Content Size Analysis Results
            ============================================
            Sample Size: ${blogContents.size}
            
            Statistics (characters):
            - Minimum:  ${"%,d".format(min)}
            - Maximum:  ${"%,d".format(max)}
            - Average:  ${"%,.0f".format(average)}
            - Median:   ${"%,d".format(median)}
            - P90:      ${"%,d".format(p90)}
            - P95:      ${"%,d".format(p95)}
            - P99:      ${"%,d".format(p99)}
            
            Distribution:
            ${distribution.joinToString("\n            ")}
            
            ============================================
            Recommendations based on analysis:
            ============================================
            
            Current Settings:
            - maxContentLength: 10,000 (covers ${contentLengths.count {
                it <= 10_000
            }}/${contentLengths.size} = ${(contentLengths.count { it <= 10_000 }.toDouble() / contentLengths.size * 100).toInt()}%)
            - maxTotalLength: 50,000
            
            Suggested Settings (to cover 95% of contents):
            - maxContentLength: ${"%,d".format((p95 * 1.2).toInt())} (P95 + 20% buffer)
            - maxTotalLength: ${"%,d".format((p95 * 1.2).toInt() * 5)} (5 contents in batch)
            
            Suggested Settings (to cover 99% of contents):
            - maxContentLength: ${"%,d".format((p99 * 1.2).toInt())} (P99 + 20% buffer)
            - maxTotalLength: ${"%,d".format((p99 * 1.2).toInt() * 5)} (5 contents in batch)
            
            Token Estimation (approximate):
            - Average content: ~${"%,.0f".format(average * 2.5)} tokens (assuming 1 char ≈ 2.5 tokens for Korean)
            - P95 content: ~${"%,d".format((p95 * 2.5).toInt())} tokens
            - P99 content: ~${"%,d".format((p99 * 2.5).toInt())} tokens
            
            Note: Gemini API maxOutputTokens is 8000. Consider if input token limits also apply.
            ============================================
            """.trimIndent()
        )

        // Top 10 longest contents for reference
        val top10 =
            blogContents
                .sortedByDescending { it.content.length }
                .take(10)

        logger.info("\nTop 10 longest contents:")
        top10.forEachIndexed { index, content ->
            logger.info(
                "${index + 1}. [${content.contentProvider?.type?.name}] ${content.title} - ${"%,d".format(content.content.length)} chars"
            )
        }
    }

    @Test
    fun `simulate batch processing with current token limits`() {
        val batchSize = 5
        val currentMaxContentLength = 10_000
        val currentMaxTotalLength = 50_000

        logger.info("Simulating batch processing with current limits...")

        // Summary가 없는 Content 조회 (실제 배치 로직과 동일)
        val pageable = PageRequest.of(0, batchSize)
        val contents =
            contentRepository
                .findContentsWithoutSummaryOrderedByProviderTypePriority(pageable)
                .content

        if (contents.isEmpty()) {
            logger.info("No unprocessed contents found")
            return
        }

        logger.info("Found ${contents.size} unprocessed contents")

        // 현재 로직으로 필터링 시뮬레이션
        val validatedContents = mutableListOf<com.nexters.external.entity.Content>()
        var totalLength = 0
        var filteredCount = 0

        contents.forEach { content ->
            val contentLength = content.content.length
            val providerType = content.contentProvider?.type?.name ?: "UNKNOWN"

            when {
                contentLength > currentMaxContentLength -> {
                    filteredCount++
                    logger.warn("Content filtered (too long): [$providerType] ${content.title} - ${"%,d".format(contentLength)} chars")
                }
                totalLength + contentLength > currentMaxTotalLength -> {
                    filteredCount++
                    logger.warn("Content filtered (batch limit): [$providerType] ${content.title} - would exceed total limit")
                }
                else -> {
                    validatedContents.add(content)
                    totalLength += contentLength
                    logger.info("Content accepted: [$providerType] ${content.title} - ${"%,d".format(contentLength)} chars")
                }
            }
        }

        logger.info(
            """
            ============================================
            Batch Processing Simulation Results
            ============================================
            Total fetched: ${contents.size}
            Accepted: ${validatedContents.size}
            Filtered: $filteredCount
            Total length: ${"%,d".format(totalLength)} chars (~${"%,d".format((totalLength * 2.5).toInt())} tokens)
            
            Current limits seem ${if (filteredCount > 0) "TOO RESTRICTIVE" else "APPROPRIATE"}
            ${if (filteredCount > 0) "Consider increasing limits based on analysis results above" else ""}
            ============================================
            """.trimIndent()
        )
    }
}
