package com.nexters.newsletter.resolver

import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.repository.ExposureContentRecommendationCandidateRow
import com.nexters.external.service.CategoryService
import com.nexters.external.service.category.ContentCategoryScoreService
import com.nexters.external.service.category.ContentCategoryScoreSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        val resolver = resolver(listOf(keywordSource, providerSource))

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
        val resolver = resolver(listOf(source))

        val pool = resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))

        assertThat(pool.expandedWindow).isEqualTo(CandidateRecencyWindow.DAYS_30)
        assertThat(pool.candidates).hasSize(130)
        assertThat(source.requests).hasSize(1)
    }

    @Test
    fun `resolveCandidatePoolByCategoryIds should keep expanding when raw pool reaches target but category fit pool does not`() {
        val fitExposureContentIds = ((1L..3L) + (131L..247L)).toSet()
        val scoresByContentId =
            (1L..260L).associate { exposureContentId ->
                val contentId = exposureContentId * 10
                contentId to
                    if (exposureContentId in fitExposureContentIds) {
                        listOf(categoryScore(contentId, categoryId = 1L, keywordScore = 4.0))
                    } else {
                        listOf(
                            categoryScore(contentId, categoryId = 1L, keywordScore = 4.0),
                            categoryScore(contentId, categoryId = 2L, keywordScore = 4.0),
                        )
                    }
            }
        val source =
            RecordingCandidateSource(
                sourceName = "bulk_source",
                sourceOrder = 1,
                sourceDefaultLimit = 130,
            ) { request ->
                val ids =
                    when (request.window) {
                        CandidateRecencyWindow.DAYS_30 -> 1L..130L
                        CandidateRecencyWindow.DAYS_90 -> 131L..260L
                        else -> LongRange.EMPTY
                    }
                ids.map { id ->
                    CandidateSeed(
                        candidate = candidate(exposureContentId = id),
                        signals = listOf(signal(source = "bulk_source")),
                    )
                }
            }
        val resolver =
            resolver(
                sources = listOf(source),
                contentCategoryScoreService = contentCategoryScoreService(scoresByContentId),
            )

        val pool = resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))

        assertThat(pool.expandedWindow).isEqualTo(CandidateRecencyWindow.DAYS_90)
        assertThat(pool.candidates).hasSize(120)
        assertThat(pool.candidates.map { it.candidate.exposureContentId }).containsOnly(*fitExposureContentIds.toTypedArray())
    }

    @Test
    fun `resolveCandidatePoolByCategoryIds should not load keyword and provider context when early source fills target pool`() {
        val categoryService = categoryService()
        val primarySource =
            RecordingCandidateSource(
                sourceName = "primary_source",
                sourceOrder = 1,
                sourceDefaultLimit = 130,
            ) { request ->
                assertThat(request.context.categoryIds).containsExactly(1L)
                (1L..130L).map { id ->
                    CandidateSeed(
                        candidate = candidate(exposureContentId = id),
                        signals = listOf(signal(source = "primary_source")),
                    )
                }
            }
        val fallbackSource =
            RecordingCandidateSource(
                sourceName = "fallback_source",
                sourceOrder = 2,
                sourceDefaultLimit = 10,
            ) {
                error("fallback source should not be called")
            }
        val resolver = resolver(listOf(primarySource, fallbackSource), categoryService)

        val pool = resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))

        assertThat(pool.candidates).hasSize(130)
        assertThat(fallbackSource.requests).isEmpty()
        verify(exactly = 0) { categoryService.getKeywordsByCategoryIds(any()) }
        verify(exactly = 0) { categoryService.getContentProvidersByCategoryIds(any()) }
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
        val resolver = resolver(listOf(source))

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

    @Test
    fun `resolveCandidatePoolByCategoryIds should prepare source context once while expanding windows`() {
        val categoryService =
            categoryService(
                keywords = listOf(ReservedKeyword(id = 10L, name = "Kotlin")),
                providers =
                    listOf(
                        ContentProvider(
                            id = 20L,
                            name = "Provider",
                            channel = "email",
                            language = "ko",
                            type = null,
                        ),
                    ),
            )
        val source =
            RecordingCandidateSource(
                sourceName = "empty_source",
                sourceOrder = 1,
                sourceDefaultLimit = 10,
            ) {
                emptyList()
            }
        val resolver = resolver(listOf(source), categoryService)

        resolver.resolveCandidatePoolByCategoryIds(userId = 1L, categoryIds = listOf(1L))

        assertThat(source.requests).hasSize(4)
        assertThat(source.requests.map { it.context.reservedKeywordIds }).containsOnly(listOf(10L))
        assertThat(source.requests.map { it.context.contentProviderIds }).containsOnly(listOf(20L))
        verify(exactly = 1) { categoryService.getKeywordsByCategoryIds(listOf(1L)) }
        verify(exactly = 1) { categoryService.getContentProvidersByCategoryIds(listOf(1L)) }
    }

    private fun resolver(
        sources: List<CandidateSource>,
        categoryService: CategoryService = categoryService(),
        contentCategoryScoreService: ContentCategoryScoreService = contentCategoryScoreService(),
    ): PossibleContentsResolver =
        PossibleContentsResolver(
            candidateSources = sources,
            categoryService = categoryService,
            contentCategoryScoreService = contentCategoryScoreService,
        )

    private fun categoryService(
        keywords: List<ReservedKeyword> = emptyList(),
        providers: List<ContentProvider> = emptyList(),
    ): CategoryService {
        val categoryService = mockk<CategoryService>()
        every { categoryService.getKeywordsByCategoryIds(any()) } returns keywords
        every { categoryService.getContentProvidersByCategoryIds(any()) } returns providers
        return categoryService
    }

    private fun contentCategoryScoreService(
        scoresByContentId: Map<Long, List<ContentCategoryScoreSnapshot>> = emptyMap(),
    ): ContentCategoryScoreService {
        val contentCategoryScoreService = mockk<ContentCategoryScoreService>()
        every { contentCategoryScoreService.getScoresByContentIds(any()) } answers {
            val contentIds = firstArg<Collection<Long>>().toSet()
            scoresByContentId.filterKeys { it in contentIds }
        }
        return contentCategoryScoreService
    }

    private fun categoryScore(
        contentId: Long,
        categoryId: Long,
        keywordScore: Double = 0.0,
        providerScore: Double = 0.0,
    ): ContentCategoryScoreSnapshot =
        ContentCategoryScoreSnapshot(
            contentId = contentId,
            categoryId = categoryId,
            keywordScore = keywordScore,
            providerScore = providerScore,
            totalScore = keywordScore + providerScore,
        )

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
