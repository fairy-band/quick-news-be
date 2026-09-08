package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TopicDeduplicationPolicyTest {

    private val policy = TopicDeduplicationPolicy()

    @Test
    fun `임베딩 유사도가 0_80 미만인 서로 다른 토픽 아티클은 감쇠 계수 1_0을 반환해야 한다`() {
        val selected = listOf(createCandidate(contentId = 1L, title = "Kafka 브로커 파티션 튜닝"))
        val candidate = createCandidate(contentId = 2L, title = "PostgreSQL 트랜잭션 격리 수준")

        // 유사도 0.45 (관련 없음)
        val similarityMap = mapOf(
            2L to mapOf(1L to 0.45),
            1L to mapOf(2L to 0.45)
        )

        val multiplier = policy.calculateDampingMultiplier(candidate, selected, similarityMap)
        assertThat(multiplier).isEqualTo(1.0)
        assertThat(policy.isTopicDuplicate(candidate, selected, similarityMap)).isFalse()
    }

    @Test
    fun `임베딩 유사도가 0_90으로 높은 동일 토픽 아티클은 MMR 감쇠 계수가 0_55 수준으로 대폭 깎여야 한다`() {
        val selected = listOf(createCandidate(contentId = 1L, title = "React 19 신규 기능 톺아보기"))
        val candidate = createCandidate(contentId = 2L, title = "리액트 19 릴리즈와 서버 컴포넌트")

        // 유사도 0.90 (매우 유사)
        val similarityMap = mapOf(
            2L to mapOf(1L to 0.90),
            1L to mapOf(2L to 0.90)
        )

        val multiplier = policy.calculateDampingMultiplier(candidate, selected, similarityMap)
        // 1.0 - ((0.90 - 0.80) / 0.20) * 0.90 = 1.0 - 0.5 * 0.90 = 0.55
        assertThat(multiplier).isCloseTo(0.55, Offset.offset(0.01))
        assertThat(policy.isTopicDuplicate(candidate, selected, similarityMap)).isTrue()
    }

    @Test
    fun `기술 키워드(React 19, 한글 리액트 19)가 일치하는 경우 임베딩이 없어도 어휘적 중복으로 감지되어야 한다`() {
        val selected = listOf(createCandidate(contentId = 10L, title = "React 19 공식 출시"))
        val candidate = createCandidate(contentId = 20L, title = "리액트 19 마이그레이션 안내")

        // 임베딩 맵이 비어있는 콜드 스타트 상황
        assertThat(policy.isTopicDuplicate(candidate, selected, emptyMap())).isTrue()
        assertThat(policy.calculateDampingMultiplier(candidate, selected, emptyMap())).isEqualTo(0.20)
    }

    @Test
    fun `제목이 서로 다른 일반 기술 글(Spring Boot vs Kafka)은 중복으로 감지되지 않아야 한다`() {
        val selected = listOf(createCandidate(contentId = 100L, title = "Spring Boot 3.3 성능 최적화 가이드"))
        val candidate = createCandidate(contentId = 200L, title = "Kafka 분산 메시징 처리 노하우")

        assertThat(policy.isTopicDuplicate(candidate, selected, emptyMap())).isFalse()
        assertThat(policy.calculateDampingMultiplier(candidate, selected, emptyMap())).isEqualTo(1.0)
    }

    private fun createCandidate(contentId: Long, title: String): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = contentId * 10,
            contentId = contentId,
            contentProviderId = contentId * 100,
            contentProviderName = "Provider $contentId",
            newsletterName = "Newsletter $contentId",
            publishedAt = LocalDate.of(2026, 6, 9),
            title = title,
            provocativeHeadline = title,
            summaryContent = "Summary for $title",
        )
}
