package com.nexters.api.service

import com.nexters.api.util.LocalCache
import com.nexters.external.repository.ExploreContentRow
import com.nexters.external.service.ExposureContentService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class NewsletterExploreServiceTest {
    private lateinit var exposureContentService: ExposureContentService
    private lateinit var sut: NewsletterExploreService

    @BeforeEach
    fun setUp() {
        deleteExploreCacheKeys()
        exposureContentService = Mockito.mock(ExposureContentService::class.java)
        sut =
            NewsletterExploreService(
                exposureContentService = exposureContentService,
            )
    }

    @Test
    fun `same cursor request should reuse cached page and total count`() {
        Mockito
            .`when`(exposureContentService.getExploreContentRows(0L, 3))
            .thenReturn(
                listOf(
                    exploreRow(id = 20L),
                    exploreRow(id = 10L),
                ),
            )
        Mockito
            .`when`(exposureContentService.countAllExposureContents())
            .thenReturn(2L)

        val first = sut.getExploreContents(lastSeenOffset = 0L, size = 2)
        val second = sut.getExploreContents(lastSeenOffset = 0L, size = 2)

        assertThat(first).isEqualTo(second)
        assertThat(first.contents).hasSize(2)
        assertThat(first.hasMore).isFalse()
        assertThat(first.nextOffset).isNull()
        assertThat(first.totalCount).isEqualTo(2L)
        Mockito
            .verify(exposureContentService, Mockito.times(1))
            .getExploreContentRows(0L, 3)
        Mockito
            .verify(exposureContentService, Mockito.times(1))
            .countAllExposureContents()
    }

    @Test
    fun `different cursor request should load a different page`() {
        Mockito
            .`when`(exposureContentService.getExploreContentRows(0L, 3))
            .thenReturn(listOf(exploreRow(id = 30L), exploreRow(id = 20L), exploreRow(id = 10L)))
        Mockito
            .`when`(exposureContentService.getExploreContentRows(20L, 3))
            .thenReturn(listOf(exploreRow(id = 10L)))
        Mockito
            .`when`(exposureContentService.countAllExposureContents())
            .thenReturn(3L)

        val first = sut.getExploreContents(lastSeenOffset = 0L, size = 2)
        val second = sut.getExploreContents(lastSeenOffset = 20L, size = 2)

        assertThat(first.hasMore).isTrue()
        assertThat(first.nextOffset).isEqualTo(20L)
        assertThat(second.contents.map { it.id }).containsExactly(10L)
        assertThat(second.hasMore).isFalse()
        Mockito
            .verify(exposureContentService, Mockito.times(1))
            .getExploreContentRows(0L, 3)
        Mockito
            .verify(exposureContentService, Mockito.times(1))
            .getExploreContentRows(20L, 3)
        Mockito
            .verify(exposureContentService, Mockito.times(1))
            .countAllExposureContents()
    }

    @Test
    fun `non first page request should not be cached`() {
        Mockito
            .`when`(exposureContentService.getExploreContentRows(20L, 3))
            .thenReturn(listOf(exploreRow(id = 10L)))
        Mockito
            .`when`(exposureContentService.countAllExposureContents())
            .thenReturn(3L)

        val first = sut.getExploreContents(lastSeenOffset = 20L, size = 2)
        val second = sut.getExploreContents(lastSeenOffset = 20L, size = 2)

        assertThat(first.contents.map { it.id }).containsExactly(10L)
        assertThat(second.contents.map { it.id }).containsExactly(10L)
        Mockito
            .verify(exposureContentService, Mockito.times(2))
            .getExploreContentRows(20L, 3)
        Mockito
            .verify(exposureContentService, Mockito.times(1))
            .countAllExposureContents()
    }

    @Test
    fun `next cursor request should not overlap with previous page`() {
        Mockito
            .`when`(exposureContentService.getExploreContentRows(0L, 3))
            .thenReturn(listOf(exploreRow(id = 50L), exploreRow(id = 40L), exploreRow(id = 30L)))
        Mockito
            .`when`(exposureContentService.getExploreContentRows(40L, 3))
            .thenReturn(listOf(exploreRow(id = 30L), exploreRow(id = 20L)))
        Mockito
            .`when`(exposureContentService.countAllExposureContents())
            .thenReturn(4L)

        val firstPage = sut.getExploreContents(lastSeenOffset = 0L, size = 2)
        val secondPage = sut.getExploreContents(lastSeenOffset = firstPage.nextOffset!!, size = 2)

        assertThat(firstPage.contents.map { it.id }).containsExactly(50L, 40L)
        assertThat(secondPage.contents.map { it.id }).containsExactly(30L, 20L)
        assertThat(firstPage.contents.map { it.id }).doesNotContainAnyElementsOf(secondPage.contents.map { it.id })
    }

    private fun exploreRow(id: Long): ExploreContentRow =
        ExploreContentRow(
            id = id,
            contentId = id + 100,
            provocativeKeyword = "Kotlin",
            provocativeHeadline = "Headline $id",
            summaryContent = "Summary $id",
            contentUrl = "https://example.com/articles/$id",
            imageUrl = null,
            newsletterName = "Newsletter",
            language = "ko",
            createdAt = LocalDateTime.of(2026, 4, 24, 10, 0),
            updatedAt = LocalDateTime.of(2026, 4, 24, 10, 0),
        )

    private fun deleteExploreCacheKeys() {
        LocalCache.delete(TOTAL_COUNT_CACHE_KEY)
        (1..50).forEach { size ->
            LocalCache.delete("exposure:contents:page:last-seen-offset:0:size:$size")
        }
    }

    private companion object {
        private const val TOTAL_COUNT_CACHE_KEY = "exposure:contents:total-count"
    }
}
