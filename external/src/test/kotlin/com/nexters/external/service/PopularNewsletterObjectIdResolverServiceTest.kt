package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ExposureContent
import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals

class PopularNewsletterObjectIdResolverServiceTest {
    private val contentRepository: ContentRepository = Mockito.mock(ContentRepository::class.java)
    private val exposureContentRepository: ExposureContentRepository = Mockito.mock(ExposureContentRepository::class.java)

    private val sut =
        PopularNewsletterObjectIdResolverService(
            contentRepository = contentRepository,
            exposureContentRepository = exposureContentRepository,
        )

    @Test
    fun `resolveObjectId should prioritize exposure content id`() {
        val exposureContent = createExposureContent(exposureContentId = 11L, contentId = 101L)

        Mockito
            .`when`(exposureContentRepository.findById(11L))
            .thenReturn(Optional.of(exposureContent))

        val result = sut.resolveObjectId("11")

        assertEquals(101L, result.resolvedContentId)
        assertEquals(11L, result.resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.RESOLVED, result.resolutionStatus)
        Mockito.verify(contentRepository, Mockito.never()).findById(11L)
    }

    @Test
    fun `resolveObjectId should resolve by content id when exposure content id lookup misses`() {
        val content = createContent(contentId = 101L)
        val exposureContent = createExposureContent(exposureContentId = 11L, content = content)

        Mockito
            .`when`(exposureContentRepository.findById(101L))
            .thenReturn(Optional.empty())
        Mockito
            .`when`(contentRepository.findById(101L))
            .thenReturn(Optional.of(content))
        Mockito
            .`when`(exposureContentRepository.findByContentId(101L))
            .thenReturn(exposureContent)

        val result = sut.resolveObjectId("101")

        assertEquals(101L, result.resolvedContentId)
        assertEquals(11L, result.resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.RESOLVED, result.resolutionStatus)
    }

    @Test
    fun `resolveObjectId should resolve by original url`() {
        val content = createContent(contentId = 101L, originalUrl = "https://example.com/articles/1")
        val exposureContent = createExposureContent(exposureContentId = 11L, content = content)

        Mockito
            .`when`(contentRepository.findByOriginalUrl("https://example.com/articles/1"))
            .thenReturn(content)
        Mockito
            .`when`(exposureContentRepository.findByContentId(101L))
            .thenReturn(exposureContent)

        val result = sut.resolveObjectId("https://example.com/articles/1")

        assertEquals(101L, result.resolvedContentId)
        assertEquals(11L, result.resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.RESOLVED, result.resolutionStatus)
    }

    @Test
    fun `resolveObjectId should prefer newsletter source content that has exposure`() {
        val olderResolvedContent =
            createContent(
                contentId = 101L,
                newsletterSourceId = "source-1",
                publishedAt = LocalDate.of(2026, 4, 10),
            )
        val latestUnresolvedContent =
            createContent(
                contentId = 102L,
                newsletterSourceId = "source-1",
                publishedAt = LocalDate.of(2026, 4, 12),
            )
        val exposureContent = createExposureContent(exposureContentId = 11L, content = olderResolvedContent)

        Mockito
            .`when`(contentRepository.findByOriginalUrl("source-1"))
            .thenReturn(null)
        Mockito
            .`when`(contentRepository.findByNewsletterSourceId("source-1"))
            .thenReturn(listOf(latestUnresolvedContent, olderResolvedContent))
        Mockito
            .`when`(exposureContentRepository.findByContentId(102L))
            .thenReturn(null)
        Mockito
            .`when`(exposureContentRepository.findByContentId(101L))
            .thenReturn(exposureContent)

        val result = sut.resolveObjectId("source-1")

        assertEquals(101L, result.resolvedContentId)
        assertEquals(11L, result.resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.RESOLVED, result.resolutionStatus)
    }

    @Test
    fun `resolveObjectId should return unresolved when no mapping exists`() {
        Mockito
            .`when`(contentRepository.findByOriginalUrl("unknown-object"))
            .thenReturn(null)
        Mockito
            .`when`(contentRepository.findByNewsletterSourceId("unknown-object"))
            .thenReturn(emptyList())

        val result = sut.resolveObjectId("unknown-object")

        assertEquals(null, result.resolvedContentId)
        assertEquals(null, result.resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.UNRESOLVED, result.resolutionStatus)
    }

    private fun createContent(
        contentId: Long,
        newsletterSourceId: String? = "newsletter-source-id",
        originalUrl: String = "https://example.com/articles/$contentId",
        publishedAt: LocalDate = LocalDate.of(2026, 4, 18),
    ): Content =
        Content(
            id = contentId,
            newsletterSourceId = newsletterSourceId,
            title = "title-$contentId",
            content = "content-$contentId",
            newsletterName = "newsletter-$contentId",
            originalUrl = originalUrl,
            imageUrl = null,
            publishedAt = publishedAt,
            contentProvider = null,
        )

    private fun createExposureContent(
        exposureContentId: Long,
        contentId: Long = 101L,
        content: Content = createContent(contentId = contentId),
    ): ExposureContent =
        ExposureContent(
            id = exposureContentId,
            content = content,
            provocativeKeyword = "keyword",
            provocativeHeadline = "headline",
            summaryContent = "summary",
        )
}
