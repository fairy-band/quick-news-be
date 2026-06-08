package com.nexters.newsletter.resolver

import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PossibleContentsResolverTest {
    @Test
    fun `resolveCandidatePoolByCategoryIds should merge candidates from multiple sources`() {
        val sharedCandidate = candidate(exposureContentId = 1)
        val keywordSource =
            fakeSource(
                name = "category_keyword",
                confidence = 0.75,
                result = sharedCandidate,
            )
        val providerSource =
            fakeSource(
                name = "category_provider",
                confidence = 0.55,
                result = sharedCandidate,
            )
        val resolver = PossibleContentsResolver(listOf(keywordSource, providerSource))

        val pool = resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))
        val sourceNames =
            pool.candidates
                .single()
                .signals
                .map { it.source }

        assertThat(pool.candidates).hasSize(1)
        assertThat(sourceNames).containsExactly("category_keyword", "category_provider")
    }

    @Test
    fun `resolveCandidatePoolByCategoryIds should stop expanding when target pool is reached`() {
        val source =
            RecordingCandidateSource(
                sourceName = "bulk_source",
                sourceOrder = 1,
                sourceDefaultLimit = 130,
            ) { request ->
                check(request.window == CandidateRecencyWindow.DAYS_30)
                (1L..130L).map { id ->
                    CandidateSeed(
                        candidate = candidate(exposureContentId = id),
                        signals = listOf(signal(source = "bulk_source")),
                    )
                }
            }
        val resolver = PossibleContentsResolver(listOf(source))

        val pool = resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))

        assertThat(pool.expandedWindow).isEqualTo(CandidateRecencyWindow.DAYS_30)
        assertThat(pool.candidates).hasSize(130)
        assertThat(source.requests).hasSize(1)
    }

    @Test
    fun `resolveCandidatePoolByCategoryIds should increase source limits while expanding windows`() {
        val source =
            RecordingCandidateSource(
                sourceName = "empty_source",
                sourceOrder = 1,
                sourceDefaultLimit = 10,
            ) {
                emptyList()
            }
        val resolver = PossibleContentsResolver(listOf(source))

        resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))

        assertThat(source.requests.map { it.limit })
            .containsExactly(10, 20, 30, 40)
        assertThat(source.requests.map { it.window })
            .containsExactly(
                CandidateRecencyWindow.DAYS_30,
                CandidateRecencyWindow.DAYS_90,
                CandidateRecencyWindow.DAYS_365,
                CandidateRecencyWindow.ALL,
            )
    }

    private fun fakeSource(
        name: String,
        confidence: Double,
        result: ExposureContentRecommendationCandidateRow,
    ): CandidateSource =
        RecordingCandidateSource(
            sourceName = name,
            sourceOrder = 1,
            sourceDefaultLimit = 10,
        ) { request ->
            if (request.window != CandidateRecencyWindow.DAYS_30) {
                return@RecordingCandidateSource emptyList()
            }

            listOf(
                CandidateSeed(
                    candidate = result,
                    signals =
                        listOf(
                            signal(
                                source = name,
                                confidence = confidence,
                            ),
                        ),
                ),
            )
        }

    private fun signal(
        source: String,
        confidence: Double = 0.7,
    ): CandidateSourceSignal =
        CandidateSourceSignal(
            source = source,
            score = 1.0,
            confidence = confidence,
            reason = "test",
        )

    private fun candidate(
        exposureContentId: Long,
        publishedAt: LocalDate = LocalDate.of(2026, 6, 9),
    ): ExposureContentRecommendationCandidateRow =
        ExposureContentRecommendationCandidateRow(
            exposureContentId = exposureContentId,
            contentId = exposureContentId * 10,
            contentProviderId = exposureContentId * 100,
            contentProviderName = "Provider $exposureContentId",
            newsletterName = "Newsletter $exposureContentId",
            publishedAt = publishedAt,
            title = "Title $exposureContentId",
            provocativeHeadline = "Headline $exposureContentId",
            summaryContent = "Summary $exposureContentId",
        )

    private class RecordingCandidateSource(
        private val sourceName: String,
        private val sourceOrder: Int,
        private val sourceDefaultLimit: Int,
        private val handler: (CandidateSourceRequest) -> List<CandidateSeed>,
    ) : CandidateSource {
        val requests = mutableListOf<CandidateSourceRequest>()

        override val name: String = sourceName
        override val order: Int = sourceOrder
        override val defaultLimit: Int = sourceDefaultLimit

        override fun fetch(request: CandidateSourceRequest): List<CandidateSeed> {
            requests += request
            return handler(request)
        }
    }
}
