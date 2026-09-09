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

    @Test
    fun `한국어 조사가 붙어있는 세부 기술 키워드(Compose의 vs Compose)도 정상 정규화되어 중복 감지되어야 한다`() {
        val selected = listOf(createCandidate(contentId = 300L, title = "Compose 완전 정복 가이드"))
        val candidate = createCandidate(contentId = 301L, title = "Compose의 상태 관리 노하우와 변경점")

        assertThat(policy.isTopicDuplicate(candidate, selected, emptyMap())).isTrue()
        assertThat(policy.calculateDampingMultiplier(candidate, selected, emptyMap())).isEqualTo(0.20)
    }

    @Test
    fun `세부 기술(Tuist) 1건만 일치해도 피드 다양성을 위해 중복으로 감지되어야 한다`() {
        val selected = listOf(createCandidate(contentId = 400L, title = "대규모 모듈 분리와 Tuist 도입기"))
        val candidate = createCandidate(contentId = 401L, title = "Tuist 4 마이그레이션 경험 공유")

        assertThat(policy.isTopicDuplicate(candidate, selected, emptyMap())).isTrue()
    }

    @Test
    fun `한글 영문 별칭(Zustand vs 주스탠드)이 달라도 동일 토픽으로 감지되어야 한다`() {
        val selected = listOf(createCandidate(contentId = 500L, title = "Zustand 상태 추적 도구 개발기"))
        val candidate = createCandidate(contentId = 501L, title = "리덕스 대신 주스탠드 도입한 후기")

        assertThat(policy.isTopicDuplicate(candidate, selected, emptyMap())).isTrue()
    }

    @Test
    fun `제목에 기술명이 모호해도 contentKeywordsMap의 세부 스택이 일치하면 중복으로 감지되어야 한다`() {
        val selected = listOf(createCandidate(contentId = 600L, title = "10년 차 개발자의 완벽한 모듈 설계 전략"))
        val candidate = createCandidate(contentId = 601L, title = "복잡한 대규모 프로젝트 구조를 풀어내는 법")

        val contentKeywordsMap = mapOf(
            600L to setOf("iOS", "Tuist", "Architecture"),
            601L to setOf("iOS", "Tuist", "모듈화")
        )

        assertThat(policy.isTopicDuplicate(candidate, selected, emptyMap(), contentKeywordsMap)).isTrue()
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
