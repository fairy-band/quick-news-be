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
        assertEquals(3.0, iosScore.competingScore)
        assertFalse(iosScore.providerMismatch)
        assertTrue(iosScore.singleCategoryFit)

        assertEquals(3.0, androidScore.keywordScore)
        assertEquals(0.0, androidScore.providerScore)
        assertEquals(3.0, androidScore.totalScore)
        assertTrue(androidScore.providerMismatch)
        assertFalse(androidScore.singleCategoryFit)
    }
}
