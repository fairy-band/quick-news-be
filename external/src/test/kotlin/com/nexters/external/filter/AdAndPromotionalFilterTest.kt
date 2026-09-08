package com.nexters.external.filter

import com.nexters.external.entity.Content
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AdAndPromotionalFilterTest {
    private val filter = AdAndPromotionalFilter()

    @Test
    fun `광고 또는 스폰서 제목은 필터링되어야 한다`() {
        val titles = listOf(
            "[광고] 이번 주말 한정 특가 세일",
            "(광고) 지금 등록하고 할인 혜택 받으세요",
            "Special Offer: 50% discount on all courses",
            "Sponsored by Acme Corp - Cloud Hosting",
            "2026 부트캠프 수강생 모집 안내",
            "개발자 커리어 컨퍼런스 사전 등록",
            "CSS Weekly 설문조사 참여하고 기프티콘 받기",
            "[채용] 시니어 백엔드 엔지니어 채용 공고",
            "Please confirm your subscription to CSS Weekly",
        )

        for (title in titles) {
            val content = createContent(title, "이것은 일반적인 내용입니다.")
            val result = filter.filter(content)
            assertFalse(result.passed, "Title '$title' should have been filtered out")
            assertTrue(result is FilterResult.Fail)
        }
    }

    @Test
    fun `정상적인 기술 블로그 및 아티클은 통과해야 한다`() {
        val normalTitles = listOf(
            "Kotlin 2.0의 스마트 캐스트 개선 사항 분석",
            "PostgreSQL 인덱스 스캔 성능 최적화 가이드",
            "Clean Architecture 실무 적용기: 멀티 모듈 구조 설계",
            "Single Prompt to Fully Functional Websites Using Mobirise AI",
            "Stop Using :invalid and :valid Pseudo-Classes. Use THIS Instead!",
        )

        for (title in normalTitles) {
            val content = createContent(title, "기술적인 본문 내용이 길게 이어집니다. ".repeat(20))
            val result = filter.filter(content)
            assertTrue(result.passed, "Title '$title' should have passed")
            assertTrue(result is FilterResult.Pass)
        }
    }

    @Test
    fun `본문이 300자 미만이며 순수 프로모션 키워드가 포함된 경우 필터링된다`() {
        val content = createContent(
            "Acme Dev Tool 출시",
            "지금 가입하면 특별 할인 쿠폰을 드립니다. use code: SAVE50",
        )
        val result = filter.filter(content)
        assertFalse(result.passed)
        assertTrue(result is FilterResult.Fail)
    }

    private fun createContent(title: String, body: String): Content =
        Content(
            id = 1L,
            title = title,
            content = body,
            newsletterName = "Test Newsletter",
            originalUrl = "https://example.com",
            publishedAt = LocalDate.now(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
}
