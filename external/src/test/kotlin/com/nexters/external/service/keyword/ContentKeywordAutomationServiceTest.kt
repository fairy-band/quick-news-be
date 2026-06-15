package com.nexters.external.service.keyword

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ContentKeywordMatchScore
import com.nexters.external.entity.KeywordAlias
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.enums.KeywordAliasMatchType
import com.nexters.external.enums.KeywordMatchSource
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentKeywordMatchScoreRepository
import com.nexters.external.repository.KeywordAliasRepository
import com.nexters.external.repository.ReservedKeywordRepository
import com.nexters.external.service.category.ContentCategoryScoreService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentKeywordAutomationServiceTest {
    private val keywordAliasRepository = mockk<KeywordAliasRepository>()
    private val reservedKeywordRepository = mockk<ReservedKeywordRepository>()
    private val contentKeywordMappingRepository = mockk<ContentKeywordMappingRepository>()
    private val contentKeywordMatchScoreRepository = mockk<ContentKeywordMatchScoreRepository>()
    private val contentCategoryScoreService = mockk<ContentCategoryScoreService>()

    private lateinit var service: ContentKeywordAutomationService

    private val savedMappings = mutableListOf<ContentKeywordMapping>()
    private val savedScores = mutableListOf<ContentKeywordMatchScore>()

    private val kotlin = ReservedKeyword(id = 1L, name = "Kotlin")
    private val redis = ReservedKeyword(id = 2L, name = "Redis")
    private val ai = ReservedKeyword(id = 3L, name = "AI")
    private val devOps = ReservedKeyword(id = 4L, name = "DevOps")

    @BeforeTest
    fun setUp() {
        savedMappings.clear()
        savedScores.clear()

        every { keywordAliasRepository.findByEnabledTrue() } returns emptyList()
        every { reservedKeywordRepository.findAll() } returns listOf(kotlin, redis, ai, devOps)
        every { contentKeywordMappingRepository.findByContentAndKeyword(any(), any()) } returns null
        every { contentKeywordMappingRepository.save(any()) } answers {
            firstArg<ContentKeywordMapping>().also(savedMappings::add)
        }
        every {
            contentKeywordMatchScoreRepository.findByContentIdAndKeywordIdAndSource(any(), any(), any())
        } returns null
        every { contentKeywordMatchScoreRepository.save(any()) } answers {
            firstArg<ContentKeywordMatchScore>().also(savedScores::add)
        }
        every { contentCategoryScoreService.recalculateForContent(any<Content>()) } returns 1

        service =
            ContentKeywordAutomationService(
                matchProviders = listOf(RuleBasedKeywordMatchProvider(keywordAliasRepository)),
                reservedKeywordRepository = reservedKeywordRepository,
                contentKeywordMappingRepository = contentKeywordMappingRepository,
                contentKeywordMatchScoreRepository = contentKeywordMatchScoreRepository,
                contentCategoryScoreService = contentCategoryScoreService,
            )
    }

    @Test
    fun `does not use AI fallback when rule matches are strong enough`() {
        val content =
            sampleContent(
                title = "Kotlin Redis guide",
                body = "Coroutine patterns for a cache-heavy backend service.",
            )

        val result = service.assignKeywords(content, aiMatchedKeywordNames = listOf("AI"))

        assertFalse(result.usedAiFallback)
        assertEquals(0, result.aiFallbackKeywordCount)
        assertEquals(listOf("Kotlin", "Redis"), savedMappings.map { it.keyword.name }.sorted())
        assertTrue(savedScores.none { it.source == KeywordMatchSource.AI_FALLBACK })
        verify(exactly = 1) { contentCategoryScoreService.recalculateForContent(any<Content>()) }
    }

    @Test
    fun `uses AI fallback when deterministic matching is weak`() {
        val content =
            sampleContent(
                title = "A small release note",
                body = "A short item without enough deterministic signals.",
            )

        val result = service.assignKeywords(content, aiMatchedKeywordNames = listOf("Kotlin"))

        assertTrue(result.usedAiFallback)
        assertEquals(1, result.aiFallbackKeywordCount)
        assertEquals(listOf("Kotlin"), savedMappings.map { it.keyword.name })
        assertEquals(KeywordMatchSource.AI_FALLBACK, savedScores.single().source)
    }

    @Test
    fun `uses keyword aliases as higher confidence source hints`() {
        every { keywordAliasRepository.findByEnabledTrue() } returns
            listOf(
                KeywordAlias(
                    keywordId = devOps.id!!,
                    alias = "DevOps Weekly",
                    normalizedAlias = "devops weekly",
                    matchType = KeywordAliasMatchType.SOURCE,
                    weight = 2.0,
                    targetFields = "SOURCE",
                ),
            )

        val content =
            sampleContent(
                title = "Issue 700",
                body = "A weekly list of infrastructure links.",
                newsletterName = "DevOps Weekly",
            )

        val result = service.assignKeywords(content)

        assertFalse(result.usedAiFallback)
        assertEquals(listOf("DevOps"), savedMappings.map { it.keyword.name })
        assertEquals(KeywordMatchSource.SOURCE_HINT, savedScores.single().source)
        assertTrue(savedScores.single().confidence >= 0.8)
    }

    private fun sampleContent(
        title: String,
        body: String,
        newsletterName: String = "Sample Newsletter",
    ): Content =
        Content(
            id = 10L,
            title = title,
            content = body,
            newsletterName = newsletterName,
            originalUrl = "https://example.com/item",
            publishedAt = LocalDate.of(2026, 6, 8),
        )
}
