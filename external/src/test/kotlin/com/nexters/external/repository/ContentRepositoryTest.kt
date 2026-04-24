package com.nexters.external.repository

import com.nexters.external.entity.Category
import com.nexters.external.entity.CategoryKeywordMapping
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.Summary
import com.nexters.external.enums.ContentProviderType
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
class ContentRepositoryTest {
    @Autowired
    lateinit var contentRepository: ContentRepository

    @Autowired
    lateinit var contentProviderRepository: ContentProviderRepository

    @Autowired
    lateinit var categoryRepository: CategoryRepository

    @Autowired
    lateinit var reservedKeywordRepository: ReservedKeywordRepository

    @Autowired
    lateinit var summaryRepository: SummaryRepository

    @Autowired
    lateinit var exposureContentRepository: ExposureContentRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private lateinit var blogProvider: ContentProvider
    private lateinit var newsletterProvider: ContentProvider
    private lateinit var category1: Category
    private lateinit var category2: Category
    private lateinit var keyword1: ReservedKeyword
    private lateinit var keyword2: ReservedKeyword

    @BeforeEach
    fun setup() {
        // Provider 생성
        blogProvider =
            contentProviderRepository.save(
                ContentProvider(
                    name = "Test Blog",
                    type = ContentProviderType.BLOG,
                    channel = "https://blog.test.com",
                    language = "EN"
                )
            )

        newsletterProvider =
            contentProviderRepository.save(
                ContentProvider(
                    name = "Test Newsletter",
                    type = ContentProviderType.NEWSLETTER,
                    channel = "https://newsletter.test.com",
                    language = "EN"
                )
            )

        // Category 생성
        category1 = categoryRepository.save(Category(name = "Tech"))
        category2 = categoryRepository.save(Category(name = "Business"))

        // Keyword 생성
        keyword1 = reservedKeywordRepository.save(ReservedKeyword(name = "AI"))
        keyword2 = reservedKeywordRepository.save(ReservedKeyword(name = "Startup"))

        // Category-Keyword 매핑 (EntityManager 사용)
        val mapping1 =
            CategoryKeywordMapping(
                category = category1,
                keyword = keyword1,
                weight = 1.0
            )
        entityManager.persist(mapping1)

        val mapping2 =
            CategoryKeywordMapping(
                category = category2,
                keyword = keyword2,
                weight = 1.0
            )
        entityManager.persist(mapping2)
        entityManager.flush()
    }

    @Test
    fun `findContentsWithoutSummaryOrderedByCategoryBalance should prioritize categories with fewer exposure contents`() {
        // given: category1에 노출 컨텐츠 2개, category2에 1개 생성
        val content1 = createContentWithKeyword("Content 1", blogProvider, keyword1)
        val content2 = createContentWithKeyword("Content 2", blogProvider, keyword1)
        val content3 = createContentWithKeyword("Content 3", blogProvider, keyword2)

        // category1 컨텐츠들에 Summary와 ExposureContent 생성 (2개)
        createSummaryAndExposure(content1)
        createSummaryAndExposure(content2)

        // category2 컨텐츠에 Summary와 ExposureContent 생성 (1개)
        createSummaryAndExposure(content3)

        // Summary가 없는 새로운 컨텐츠들 생성
        val newContent1 = createContentWithKeyword("New Content 1", blogProvider, keyword1) // category1
        val newContent2 = createContentWithKeyword("New Content 2", blogProvider, keyword2) // category2

        // when: 배치 대상 조회
        val pageable = PageRequest.of(0, 10)
        val result = contentRepository.findContentsWithoutSummaryOrderedByCategoryBalance(10_000, pageable)

        // then: category2(노출 1개)의 컨텐츠가 category1(노출 2개)보다 먼저 조회되어야 함
        assertEquals(2, result.content.size)
        assertEquals(newContent2.id, result.content[0].id) // category2 컨텐츠가 먼저
        assertEquals(newContent1.id, result.content[1].id) // category1 컨텐츠가 나중
    }

    @Test
    fun `findContentsWithoutSummaryOrderedByCategoryBalance should prioritize BLOG over NEWSLETTER`() {
        // given: 동일한 카테고리에 BLOG와 NEWSLETTER 컨텐츠 생성
        val blogContent = createContentWithKeyword("Blog Content", blogProvider, keyword1)
        val newsletterContent = createContentWithKeyword("Newsletter Content", newsletterProvider, keyword1)

        // when
        val pageable = PageRequest.of(0, 10)
        val result = contentRepository.findContentsWithoutSummaryOrderedByCategoryBalance(10_000, pageable)

        // then: BLOG가 NEWSLETTER보다 먼저 조회
        assertEquals(2, result.content.size)
        assertEquals(blogContent.id, result.content[0].id)
        assertEquals(newsletterContent.id, result.content[1].id)
    }

    @Test
    fun `findContentsWithoutSummaryOrderedByCategoryBalance should exclude contents with summary`() {
        // given
        val contentWithSummary = createContentWithKeyword("Content With Summary", blogProvider, keyword1)
        val contentWithoutSummary = createContentWithKeyword("Content Without Summary", blogProvider, keyword1)

        // Summary 생성
        summaryRepository.save(
            Summary(
                content = contentWithSummary,
                title = "Summary Title",
                summarizedContent = "Summary Content",
                model = "test-model"
            )
        )

        // when
        val pageable = PageRequest.of(0, 10)
        val result = contentRepository.findContentsWithoutSummaryOrderedByCategoryBalance(10_000, pageable)

        // then: Summary가 없는 컨텐츠만 조회
        assertEquals(1, result.content.size)
        assertEquals(contentWithoutSummary.id, result.content[0].id)
    }

    @Test
    fun `findContentsWithoutSummaryOrderedByCategoryBalance should order by createdAt DESC when same conditions`() {
        // given: 동일한 조건의 컨텐츠들 생성
        val olderContent =
            createContentWithKeyword(
                "Older Content",
                blogProvider,
                keyword1,
                createdAt = LocalDateTime.now().minusDays(2)
            )
        val newerContent =
            createContentWithKeyword(
                "Newer Content",
                blogProvider,
                keyword1,
                createdAt = LocalDateTime.now().minusDays(1)
            )

        // when
        val pageable = PageRequest.of(0, 10)
        val result = contentRepository.findContentsWithoutSummaryOrderedByCategoryBalance(10_000, pageable)

        // then: 최신 컨텐츠가 먼저 조회
        assertEquals(2, result.content.size)
        assertEquals(newerContent.id, result.content[0].id)
        assertEquals(olderContent.id, result.content[1].id)
    }

    private fun createContentWithKeyword(
        title: String,
        provider: ContentProvider,
        keyword: ReservedKeyword,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): Content =
        contentRepository.save(
            Content(
                title = title,
                content = "Test content with sufficient length to pass validation. ".repeat(20),
                newsletterName = "Test Newsletter",
                originalUrl = "https://test.com/$title",
                publishedAt = LocalDate.now(),
                contentProvider = provider,
                reservedKeywords = setOf(keyword),
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )

    private fun createSummaryAndExposure(content: Content) {
        val summary =
            summaryRepository.save(
                Summary(
                    content = content,
                    title = "Summary for ${content.title}",
                    summarizedContent = "Summarized content",
                    model = "test-model"
                )
            )

        exposureContentRepository.save(
            ExposureContent(
                content = content,
                provocativeKeyword = "Test Keyword",
                provocativeHeadline = "Test Headline",
                summaryContent = "Test Summary"
            )
        )
    }
}
