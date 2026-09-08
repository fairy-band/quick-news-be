package com.nexters.external.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownValidatorTest {

    @Test
    fun `신규 30초 스캔용 규격 마크다운은 유효성 검증을 통과해야 한다`() {
        val markdown = """
            # React 19 핵심 변경점 총정리
            
            > ⏱️ **예상 완독 시간**: 약 2분 30초
            
            ## ⚡ 3줄 핵심 요약 (TL;DR)
            • React 19 정식 릴리즈로 새로운 액션 훅과 서버 컴포넌트가 안정화되었습니다.
            • useActionState 및 useOptimistic 훅을 통해 비동기 상태 관리가 단순화되었습니다.
            • React 컴파일러가 자동 메모이제이션을 지원하여 성능 최적화 비용이 감소했습니다.
            
            ## 💡 실무 적용 포인트 (Key Takeaway)
            • 기존 useMemo, useCallback 보일러플레이트를 줄이고 useActionState 전환을 검토하세요.
            
            ---
            
            ## 📖 본문 상세 내용
            
            React 19 버전의 주요 개선 사항을 심층적으로 살펴봅니다.
            본문이 길게 이어지는 내용입니다. 최소 길이 요건인 300자를 충족하기 위해 충분한 분량의 기술적 설명과 팩트를 추가합니다.
            서버 액션과 낙관적 업데이트는 폼 처리에서 획기적인 생산성 향상을 제공합니다.
            
            ---
            
            ## 🎯 결론 및 시사점
            
            마이그레이션 가이드를 참고하여 점진적으로 적용하는 것을 권장합니다.
            
            👉 [원문 아티클 전체 보기](https://example.com/react-19)
        """.trimIndent()

        assertThat(MarkdownValidator.isValid(markdown)).isTrue()
    }

    @Test
    fun `기존 레거시 헤더 구조의 마크다운도 하위 호환되어 통과해야 한다`() {
        val legacyMarkdown = """
            # PostgreSQL 트랜잭션 격리 수준 가이드
            
            ## 💡 핵심 요약
            • Read Committed와 Repeatable Read 격리 수준의 내부 동작 원리를 비교합니다.
            • MVCC 아키텍처에서 팬텀 리드와 직렬화 실패가 어떻게 처리되는지 다룹니다.
            • 데드락을 방지하기 위한 인덱스 정렬 및 잠금 순서 설계 원칙을 소개합니다.
            
            ---
            
            ## 📖 본문 상세 내용
            
            트랜잭션 격리 수준은 분산 데이터베이스 환경에서 동시성과 일관성 간의 트레이드오프를 결정합니다.
            300자 이상의 충분한 텍스트 분량을 확보하여 검증을 통과하도록 기술적인 배경과 내부 구조를 상세히 기술합니다.
            
            ---
            
            ## 🎯 결론 및 실무 시사점
            
            격리 수준을 불필요하게 높이지 않고, 낙관적 락을 적절히 결합하는 것이 최선의 아키텍처입니다.
            
            👉 [원문 아티클 전체 보기](https://example.com/postgres-guide)
        """.trimIndent()

        assertThat(MarkdownValidator.isValid(legacyMarkdown)).isTrue()
    }

    @Test
    fun `예상 완독 시간 계산이 글자 수에 맞게 올바르게 산출되어야 한다`() {
        val shortText = "짧은 텍스트 ".repeat(10)
        assertThat(MarkdownValidator.estimateReadingTime(shortText)).isEqualTo("약 1분 이내")

        val mediumText = "기술 블로그 본문 내용 테스트입니다. ".repeat(40) // ~800자
        val result = MarkdownValidator.estimateReadingTime(mediumText)
        assertThat(result).contains("분")
    }

    @Test
    fun `standardizeMarkdown은 레거시 마크다운에 완독 시간과 원문 링크를 자동으로 보강해야 한다`() {
        val legacyWithoutTime = """
            # Kafka 파티션 설계 원칙
            
            ## 💡 핵심 요약
            • 파티션 수 산정 공식과 브로커 부하 분산 전략을 설명합니다.
            • 컨슈머 랙 모니터링을 통한 병목 감지 기법을 소개합니다.
            • 리밸런싱 비용을 최소화하는 파티션 할당 전략을 다룹니다.
            
            ## 📖 본문 상세 내용
            카프카 클러스터의 확장성과 처리량은 올바른 파티션 수 설정에 달려 있습니다.
        """.trimIndent()

        val standardized = MarkdownValidator.standardizeMarkdown(legacyWithoutTime, "https://example.com/kafka")

        assertThat(standardized).contains("⏱️ **예상 완독 시간**")
        assertThat(standardized).contains("👉 [원문 아티클 전체 보기](https://example.com/kafka)")
    }
}
