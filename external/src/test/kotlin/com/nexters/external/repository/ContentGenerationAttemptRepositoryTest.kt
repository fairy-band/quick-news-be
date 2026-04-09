package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentGenerationAttempt
import com.nexters.external.entity.Summary
import com.nexters.external.enums.ContentGenerationMode
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
class ContentGenerationAttemptRepositoryTest {
    @Autowired
    lateinit var contentRepository: ContentRepository

    @Autowired
    lateinit var contentGenerationAttemptRepository: ContentGenerationAttemptRepository

    @Autowired
    lateinit var summaryRepository: SummaryRepository

    @Test
    fun `stores failed and accepted attempts and exposes accepted summary quality metadata`() {
        val savedContent =
            contentRepository.saveAndFlush(
                Content(
                    title = "테스트 콘텐츠",
                    content = "콘텐츠 본문",
                    newsletterName = "테스트 뉴스레터",
                    originalUrl = "https://example.com/content",
                    publishedAt = LocalDate.of(2026, 4, 2),
                    createdAt = LocalDateTime.of(2026, 4, 2, 10, 0),
                    updatedAt = LocalDateTime.of(2026, 4, 2, 10, 0),
                )
            )

        val failedAttempt =
            contentGenerationAttemptRepository.saveAndFlush(
                ContentGenerationAttempt(
                    content = savedContent,
                    generationMode = ContentGenerationMode.SINGLE,
                    attemptNumber = 1,
                    model = "gemini-2.5-flash",
                    promptVersion = "auto-generation-v2-human-like",
                    generatedSummary = "무난하지만 AI 티가 나는 요약",
                    generatedHeadlines = """["AI가 쓴 듯한 헤드라인"]""",
                    matchedKeywords = """["kotlin"]""",
                    qualityScore = 4,
                    qualityReason = "문장이 매끈하지만 지나치게 평탄합니다.",
                    aiLikePatterns = """["상투적 마무리"]""",
                    recommendedFix = "구체적인 상황어와 관찰 어조를 사용하세요.",
                    passed = false,
                    accepted = false,
                    retryCount = 0,
                    createdAt = LocalDateTime.of(2026, 4, 2, 10, 1),
                    updatedAt = LocalDateTime.of(2026, 4, 2, 10, 1),
                )
            )

        val acceptedAttempt =
            contentGenerationAttemptRepository.saveAndFlush(
                ContentGenerationAttempt(
                    content = savedContent,
                    generationMode = ContentGenerationMode.SINGLE,
                    attemptNumber = 2,
                    model = "gemini-2.5-flash",
                    promptVersion = "auto-generation-v2-human-like",
                    generatedSummary = "팀이 실제로 궁금해할 맥락을 남긴 요약",
                    generatedHeadlines = """["Kotlin 코루틴 디버깅, 팀이 자주 놓치는 한 지점"]""",
                    matchedKeywords = """["kotlin","coroutine"]""",
                    qualityScore = 8,
                    qualityReason = "문장 결이 자연스럽고 과장 표현이 적습니다.",
                    aiLikePatterns = """["없음"]""",
                    recommendedFix = "현재 결과 유지",
                    passed = true,
                    accepted = true,
                    retryCount = 1,
                    createdAt = LocalDateTime.of(2026, 4, 2, 10, 2),
                    updatedAt = LocalDateTime.of(2026, 4, 2, 10, 2),
                )
            )

        summaryRepository.saveAndFlush(
            Summary(
                content = savedContent,
                title = "Kotlin 코루틴 디버깅, 팀이 자주 놓치는 한 지점",
                summarizedContent = "팀이 실제로 궁금해할 맥락을 남긴 요약",
                generationAttempt = acceptedAttempt,
                qualityScore = 8,
                qualityReason = "문장 결이 자연스럽고 과장 표현이 적습니다.",
                retryCount = 1,
                summarizedAt = LocalDateTime.of(2026, 4, 2, 10, 3),
                model = "gemini-2.5-flash",
                createdAt = LocalDateTime.of(2026, 4, 2, 10, 3),
                updatedAt = LocalDateTime.of(2026, 4, 2, 10, 3),
            )
        )

        val attempts = contentGenerationAttemptRepository.findByContentOrderByCreatedAtDesc(savedContent)
        val summaries = summaryRepository.findByContent(savedContent)

        then(attempts).hasSize(2)
        then(attempts.first().id).isEqualTo(acceptedAttempt.id)
        then(attempts.first().accepted).isTrue()
        then(attempts.last().id).isEqualTo(failedAttempt.id)
        then(attempts.last().passed).isFalse()

        then(summaries).hasSize(1)
        then(summaries.first().generationAttempt?.id).isEqualTo(acceptedAttempt.id)
        then(summaries.first().qualityScore).isEqualTo(8)
        then(summaries.first().qualityReason).isEqualTo("문장 결이 자연스럽고 과장 표현이 적습니다.")
        then(summaries.first().retryCount).isEqualTo(1)
    }
}
