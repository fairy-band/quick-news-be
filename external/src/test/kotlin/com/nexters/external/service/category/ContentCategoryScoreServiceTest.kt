package com.nexters.external.service.category

import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ContentProviderCategoryMapping
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.enums.ContentProviderType
import com.nexters.external.repository.CategoryRepository
import com.nexters.external.repository.ContentCategoryScoreRepository
import com.nexters.external.repository.ContentKeywordMappingRepository
import com.nexters.external.repository.ContentProviderCategoryMappingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentCategoryScoreServiceTest {
    private val contentKeywordMappingRepository = mockk<ContentKeywordMappingRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val contentProviderCategoryMappingRepository = mockk<ContentProviderCategoryMappingRepository>()
    private val contentCategoryScoreRepository = mockk<ContentCategoryScoreRepository>()

    private lateinit var service: ContentCategoryScoreService

    @BeforeTest
    fun setUp() {
        service =
            ContentCategoryScoreService(
                contentKeywordMappingRepository = contentKeywordMappingRepository,
                categoryRepository = categoryRepository,
                contentProviderCategoryMappingRepository = contentProviderCategoryMappingRepository,
                contentCategoryScoreRepository = contentCategoryScoreRepository,
            )
    }

    @Test
    fun `recalculateForContent saves category scores using keyword and capped provider weights`() {
        val ios = Category(id = 3L, name = "iOS")
        val android = Category(id = 4L, name = "Android")
        val swift = ReservedKeyword(id = 30L, name = "Swift")
        val mobile = ReservedKeyword(id = 59L, name = "Mobile")
        val provider =
            ContentProvider(
                id = 43L,
                name = "Awesome iOS Weekly",
                channel = "awesome-ios-weekly",
                language = "en",
                type = ContentProviderType.NEWSLETTER,
            )
        val content =
            Content(
                id = 100L,
                title = "Swift and Mobile",
                content = "Swift and Mobile article",
                newsletterName = provider.name,
                originalUrl = "https://example.com/swift",
                publishedAt = LocalDate.of(2026, 6, 15),
                contentProvider = provider,
            )
        val savedScores = slot<Iterable<com.nexters.external.entity.ContentCategoryScore>>()

        every { contentKeywordMappingRepository.findByContent(content) } returns
            listOf(
                ContentKeywordMapping(content = content, keyword = swift),
                ContentKeywordMapping(content = content, keyword = mobile),
            )
        every { categoryRepository.findCategoryKeywordMappingByKeywordIds(listOf(swift.id!!, mobile.id!!)) } returns
            listOf(
                CategoryKeywordMapping(category = ios, keyword = swift, weight = 4.0),
                CategoryKeywordMapping(category = ios, keyword = mobile, weight = 3.0),
                CategoryKeywordMapping(category = android, keyword = mobile, weight = 3.0),
            )
        every { contentProviderCategoryMappingRepository.findByContentProviderIdIn(listOf(provider.id!!)) } returns
            listOf(
                ContentProviderCategoryMapping(
                    contentProvider = provider,
                    category = ios,
                    weight = 25.0,
                ),
            )
        every { contentCategoryScoreRepository.deleteByContentId(content.id!!) } returns 0
        every { contentCategoryScoreRepository.saveAll(capture(savedScores)) } answers { savedScores.captured.toList() }

        val scoreCount = service.recalculateForContent(content)

        val scoresByCategory = savedScores.captured.associateBy { it.categoryId }
        val iosScore = scoresByCategory.getValue(ios.id!!)
        val androidScore = scoresByCategory.getValue(android.id!!)

        assertEquals(2, scoreCount)
        assertEquals(7.0, iosScore.keywordScore)
        assertEquals(4.0, iosScore.providerScore)
        assertEquals(11.0, iosScore.totalScore)
        assertEquals(android.id, iosScore.competingCategoryId)
        assertEquals(1.4, iosScore.competingScore)
        assertFalse(iosScore.providerMismatch)
        assertTrue(iosScore.singleCategoryFit)

        assertEquals(1.4, androidScore.keywordScore)
        assertEquals(0.0, androidScore.providerScore)
        assertEquals(1.4, androidScore.totalScore)
        assertTrue(androidScore.providerMismatch)
        assertFalse(androidScore.singleCategoryFit)
    }

    @Test
    fun `recalculateForContent incorporates centroid embedding similarities and deducts semantic score`() {
        val keywordEmbeddingRepository = mockk<com.nexters.external.repository.KeywordEmbeddingRepository>()
        val serviceWithEmbeddings =
            ContentCategoryScoreService(
                contentKeywordMappingRepository = contentKeywordMappingRepository,
                categoryRepository = categoryRepository,
                contentProviderCategoryMappingRepository = contentProviderCategoryMappingRepository,
                contentCategoryScoreRepository = contentCategoryScoreRepository,
                keywordEmbeddingRepository = keywordEmbeddingRepository,
            )

        val ios = Category(id = 3L, name = "iOS")
        val be = Category(id = 1L, name = "BE")
        val swift = ReservedKeyword(id = 30L, name = "SwiftUI")
        val security = ReservedKeyword(id = 50L, name = "Security")
        val provider =
            ContentProvider(
                id = 148L,
                name = "Level Up Coding",
                channel = "level-up-coding",
                language = "en",
                type = ContentProviderType.BLOG,
            )
        val content =
            Content(
                id = 20761L,
                title = "Protecting SwiftUI Views with Authentication",
                content = "SwiftUI Authentication content",
                newsletterName = provider.name,
                originalUrl = "https://example.com/swiftui-auth",
                publishedAt = LocalDate.of(2026, 6, 15),
                contentProvider = provider,
            )
        val savedScores = slot<Iterable<com.nexters.external.entity.ContentCategoryScore>>()

        every { contentKeywordMappingRepository.findByContent(content) } returns
            listOf(
                ContentKeywordMapping(content = content, keyword = swift),
                ContentKeywordMapping(content = content, keyword = security),
            )
        every { categoryRepository.findCategoryKeywordMappingByKeywordIds(listOf(swift.id!!, security.id!!)) } returns
            listOf(
                CategoryKeywordMapping(category = ios, keyword = swift, weight = 8.0),
                CategoryKeywordMapping(category = be, keyword = security, weight = 5.0),
            )
        every { contentProviderCategoryMappingRepository.findByContentProviderIdIn(listOf(provider.id!!)) } returns
            listOf(
                ContentProviderCategoryMapping(contentProvider = provider, category = be, weight = 10.0),
                ContentProviderCategoryMapping(contentProvider = provider, category = ios, weight = 10.0),
            )
        every { keywordEmbeddingRepository.findCategorySimilaritiesByContentId(content.id!!) } returns
            listOf(
                object : com.nexters.external.repository.CategorySimilarityProjection {
                    override val categoryId: Long = 3L // iOS
                    override val similarity: Double = 0.6519
                },
                object : com.nexters.external.repository.CategorySimilarityProjection {
                    override val categoryId: Long = 1L // BE
                    override val similarity: Double = 0.5574
                },
            )
        every { contentCategoryScoreRepository.deleteByContentId(content.id!!) } returns 0
        every { contentCategoryScoreRepository.saveAll(capture(savedScores)) } answers { savedScores.captured.toList() }

        val scoreCount = serviceWithEmbeddings.recalculateForContent(content)

        val scoresByCategory = savedScores.captured.associateBy { it.categoryId }
        val iosScore = scoresByCategory.getValue(ios.id!!)
        val beScore = scoresByCategory.getValue(be.id!!)

        assertEquals(2, scoreCount)
        assertEquals(8.0, iosScore.keywordScore)
        assertEquals(4.0, iosScore.providerScore)
        assertEquals(0.0, iosScore.semanticScore)
        assertEquals(12.0, iosScore.totalScore)
        assertFalse(iosScore.providerMismatch)
        assertTrue(iosScore.singleCategoryFit)

        // BE should have penalty: 5.0 - 0.4*(8.0 - 5.0) = 3.8
        assertEquals(3.8, beScore.keywordScore)
        // BE fails provider guardrail due to low similarity (0.5574 < 0.6519 - 0.03): providerScore = 0.0
        assertEquals(0.0, beScore.providerScore)
        // BE semanticScore: (0.5574 - 0.6519) * 40.0 = -3.78 -> rounded to -3.8
        assertEquals(-3.8, beScore.semanticScore)
        // Total score: max(0.0, 3.8 - 3.8 + 0.0) = 0.0
        assertEquals(0.0, beScore.totalScore)
        assertFalse(beScore.singleCategoryFit)
    }
}
