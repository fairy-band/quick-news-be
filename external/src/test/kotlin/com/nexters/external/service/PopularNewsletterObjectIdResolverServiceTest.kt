package com.nexters.external.service

import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.repository.ContentLookupRow
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentLookupRow
import com.nexters.external.repository.ExposureContentRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate
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
    fun `resolveObjectIds should resolve mixed inputs with bulk lookups`() {
        Mockito
            .`when`(exposureContentRepository.findLookupRowsByIds(setOf(11L, 101L)))
            .thenReturn(listOf(ExposureContentLookupRow(id = 11L, contentId = 201L)))
        Mockito
            .`when`(contentRepository.findLookupRowsByIds(setOf(101L)))
            .thenReturn(
                listOf(
                    ContentLookupRow(
                        id = 101L,
                        originalUrl = "https://example.com/articles/101",
                        newsletterSourceId = "source-101",
                        publishedAt = LocalDate.of(2026, 4, 18),
                    ),
                ),
            )
        Mockito
            .`when`(
                contentRepository.findLookupRowsByOriginalUrls(
                    setOf("https://example.com/articles/1", "source-1", "unknown-object"),
                ),
            ).thenReturn(
                listOf(
                    ContentLookupRow(
                        id = 301L,
                        originalUrl = "https://example.com/articles/1",
                        newsletterSourceId = "source-301",
                        publishedAt = LocalDate.of(2026, 4, 18),
                    ),
                ),
            )
        Mockito
            .`when`(contentRepository.findLookupRowsByNewsletterSourceIds(setOf("source-1", "unknown-object")))
            .thenReturn(
                listOf(
                    ContentLookupRow(
                        id = 402L,
                        originalUrl = "https://example.com/articles/402",
                        newsletterSourceId = "source-1",
                        publishedAt = LocalDate.of(2026, 4, 12),
                    ),
                    ContentLookupRow(
                        id = 401L,
                        originalUrl = "https://example.com/articles/401",
                        newsletterSourceId = "source-1",
                        publishedAt = LocalDate.of(2026, 4, 10),
                    ),
                ),
            )
        Mockito
            .`when`(exposureContentRepository.findLookupRowsByContentIds(setOf(101L)))
            .thenReturn(listOf(ExposureContentLookupRow(id = 111L, contentId = 101L)))
        Mockito
            .`when`(exposureContentRepository.findLookupRowsByContentIds(setOf(301L)))
            .thenReturn(listOf(ExposureContentLookupRow(id = 311L, contentId = 301L)))
        Mockito
            .`when`(exposureContentRepository.findLookupRowsByContentIds(setOf(402L, 401L)))
            .thenReturn(listOf(ExposureContentLookupRow(id = 411L, contentId = 401L)))

        val result =
            sut.resolveObjectIds(
                listOf(
                    "11",
                    "101",
                    "https://example.com/articles/1",
                    "source-1",
                    "unknown-object",
                ),
            )

        assertEquals(
            PopularNewsletterObjectResolution(
                resolvedContentId = 201L,
                resolvedExposureContentId = 11L,
                resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
            ),
            result["11"],
        )
        assertEquals(
            PopularNewsletterObjectResolution(
                resolvedContentId = 101L,
                resolvedExposureContentId = 111L,
                resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
            ),
            result["101"],
        )
        assertEquals(
            PopularNewsletterObjectResolution(
                resolvedContentId = 301L,
                resolvedExposureContentId = 311L,
                resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
            ),
            result["https://example.com/articles/1"],
        )
        assertEquals(
            PopularNewsletterObjectResolution(
                resolvedContentId = 401L,
                resolvedExposureContentId = 411L,
                resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
            ),
            result["source-1"],
        )
        assertEquals(
            PopularNewsletterObjectResolution(
                resolutionStatus = PopularNewsletterResolutionStatus.UNRESOLVED,
            ),
            result["unknown-object"],
        )

        Mockito.verify(exposureContentRepository, Mockito.never()).findById(Mockito.anyLong())
        Mockito.verify(contentRepository, Mockito.never()).findById(Mockito.anyLong())
        Mockito.verify(contentRepository, Mockito.never()).findByOriginalUrl(Mockito.anyString())
        Mockito.verify(contentRepository, Mockito.never()).findByNewsletterSourceId(Mockito.anyString())
        Mockito.verify(exposureContentRepository, Mockito.never()).findByContentId(Mockito.anyLong())
    }

    @Test
    fun `resolveObjectId should delegate to bulk resolver`() {
        Mockito
            .`when`(exposureContentRepository.findLookupRowsByIds(setOf(11L)))
            .thenReturn(listOf(ExposureContentLookupRow(id = 11L, contentId = 101L)))

        val result = sut.resolveObjectId("11")

        assertEquals(101L, result.resolvedContentId)
        assertEquals(11L, result.resolvedExposureContentId)
        assertEquals(PopularNewsletterResolutionStatus.RESOLVED, result.resolutionStatus)
    }
}
