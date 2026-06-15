package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentKeywordMapping
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ExposureContent
import com.nexters.external.entity.ReservedKeyword
import com.nexters.external.entity.User
import com.nexters.external.entity.UserExposedContentMapping
import com.nexters.external.enums.ContentProviderType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.JpaSort
import java.time.LocalDate

@DataJpaTest
class ExposureContentRepositoryTest {
    @Autowired
    private lateinit var repository: ExposureContentRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `findExploreRows should return projection fields ordered by newest exposure content id`() {
        val provider =
            entityManager.persist(
                ContentProvider(
                    name = "Kotlin Weekly",
                    channel = "kotlin-weekly",
                    language = "ko",
                    type = ContentProviderType.NEWSLETTER,
                ),
            )
        val firstContent = entityManager.persist(content(provider = provider, title = "First"))
        val secondContent = entityManager.persist(content(provider = provider, title = "Second"))
        val firstExposureContent = entityManager.persist(exposureContent(firstContent, "First headline"))
        val secondExposureContent = entityManager.persist(exposureContent(secondContent, "Second headline"))
        entityManager.flush()
        entityManager.clear()

        val byIdDesc = JpaSort.unsafe(Sort.Direction.DESC, "e.id")
        val byIdAsc = JpaSort.unsafe(Sort.Direction.ASC, "e.id")
        val rows = repository.findExploreRows(PageRequest.of(0, 2, byIdDesc))
        val rowsAfterNewest =
            repository.findExploreRowsAfter(
                lastSeenOffset = secondExposureContent.id!!,
                pageable = PageRequest.of(0, 2, byIdDesc),
            )
        val rowsAscending = repository.findExploreRows(PageRequest.of(0, 2, byIdAsc))
        val rowsAfterOldest =
            repository.findExploreRowsAfterAscending(
                lastSeenOffset = firstExposureContent.id!!,
                pageable = PageRequest.of(0, 2, byIdAsc),
            )

        assertThat(rows).hasSize(2)
        assertThat(rows[0].id).isEqualTo(secondExposureContent.id)
        assertThat(rows[0].contentId).isEqualTo(secondContent.id)
        assertThat(rows[0].provocativeHeadline).isEqualTo("Second headline")
        assertThat(rows[0].contentUrl).isEqualTo(secondContent.originalUrl)
        assertThat(rows[0].imageUrl).isEqualTo(secondContent.imageUrl)
        assertThat(rows[0].newsletterName).isEqualTo(secondContent.newsletterName)
        assertThat(rows[0].language).isEqualTo("ko")
        assertThat(rowsAfterNewest.map { it.id }).containsExactly(firstExposureContent.id)
        assertThat(rowsAscending.map { it.id }).containsExactly(firstExposureContent.id, secondExposureContent.id)
        assertThat(rowsAfterOldest.map { it.id }).containsExactly(secondExposureContent.id)
    }

    @Test
    fun `findExploreRowsAfterByPublishedAt should support descending and ascending direction`() {
        val provider =
            entityManager.persist(
                ContentProvider(
                    name = "Kotlin Weekly",
                    channel = "kotlin-weekly",
                    language = "ko",
                    type = ContentProviderType.NEWSLETTER,
                ),
            )
        val olderContent =
            entityManager.persist(
                content(
                    provider = provider,
                    title = "Older",
                    publishedAt = LocalDate.of(2026, 4, 20),
                ),
            )
        val newerContent =
            entityManager.persist(
                content(
                    provider = provider,
                    title = "Newer",
                    publishedAt = LocalDate.of(2026, 4, 24),
                ),
            )
        val olderExposureContent = entityManager.persist(exposureContent(olderContent, "Older headline"))
        val newerExposureContent = entityManager.persist(exposureContent(newerContent, "Newer headline"))
        entityManager.flush()
        entityManager.clear()

        val byPublishedAtDesc =
            JpaSort
                .unsafe(Sort.Direction.DESC, "c.publishedAt")
                .and(JpaSort.unsafe(Sort.Direction.DESC, "e.id"))
        val byPublishedAtAsc =
            JpaSort
                .unsafe(Sort.Direction.ASC, "c.publishedAt")
                .and(JpaSort.unsafe(Sort.Direction.ASC, "e.id"))

        val rowsAfterNewest =
            repository.findExploreRowsAfterByPublishedAt(
                lastSeenPublishedAt = newerContent.publishedAt,
                pageable = PageRequest.of(0, 2, byPublishedAtDesc),
            )
        val rowsAfterOldest =
            repository.findExploreRowsAfterByPublishedAtAscending(
                lastSeenPublishedAt = olderContent.publishedAt,
                pageable = PageRequest.of(0, 2, byPublishedAtAsc),
            )

        assertThat(rowsAfterNewest.map { it.id }).containsExactly(olderExposureContent.id)
        assertThat(rowsAfterOldest.map { it.id }).containsExactly(newerExposureContent.id)
    }

    @Test
    fun `findNotExposedRecommendationCandidatesByReservedKeywordIds should return bounded non-exposed candidate rows`() {
        // Given
        val provider =
            entityManager.persist(
                ContentProvider(
                    name = "Kotlin Weekly",
                    channel = "kotlin-weekly",
                    language = "ko",
                    type = ContentProviderType.NEWSLETTER,
                ),
            )
        val keyword1 = entityManager.persist(ReservedKeyword(name = "Kotlin"))
        val keyword2 = entityManager.persist(ReservedKeyword(name = "Java"))
        val keyword3 = entityManager.persist(ReservedKeyword(name = "Spring"))

        val content1 = entityManager.persist(content(provider = provider, title = "First"))
        val content2 = entityManager.persist(content(provider = provider, title = "Second"))
        val content3 = entityManager.persist(content(provider = provider, title = "Third"))

        entityManager.persist(ContentKeywordMapping(content = content1, keyword = keyword1))
        entityManager.persist(ContentKeywordMapping(content = content2, keyword = keyword2))
        entityManager.persist(ContentKeywordMapping(content = content2, keyword = keyword1))
        entityManager.persist(ContentKeywordMapping(content = content3, keyword = keyword3))

        val exp1 = entityManager.persist(exposureContent(content1, "Kotlin headline"))
        val exp2 = entityManager.persist(exposureContent(content2, "Java headline"))
        val exp3 = entityManager.persist(exposureContent(content3, "Spring headline"))

        // Create a User to satisfy foreign key constraints
        val user = entityManager.persist(User(deviceToken = "test-device-token"))
        val userId = user.id!!

        // Expose content1 to user
        entityManager.persist(UserExposedContentMapping(userId = userId, contentId = content1.id!!))

        entityManager.flush()
        entityManager.clear()

        // When
        val candidates =
            repository.findNotExposedRecommendationCandidatesByReservedKeywordIds(
                userId = userId,
                reservedKeywordIds = listOf(keyword1.id!!, keyword2.id!!, keyword3.id!!),
                publishedFrom = LocalDate.of(2026, 1, 1),
                pageable = PageRequest.of(0, 10),
            )

        // Then
        // Should only contain exp2 and exp3 (since exp1 is already exposed)
        assertThat(candidates.map { it.exposureContentId }).containsExactlyInAnyOrder(exp2.id, exp3.id)
        assertThat(candidates.map { it.contentId }).containsExactlyInAnyOrder(content2.id, content3.id)
        assertThat(candidates.map { it.contentProviderName }).containsOnly(provider.name)
        assertThat(candidates.map { it.title }).containsExactlyInAnyOrder(content2.title, content3.title)
        assertThat(candidates.map { it.provocativeHeadline }).containsExactlyInAnyOrder(exp2.provocativeHeadline, exp3.provocativeHeadline)
        assertThat(candidates.map { it.summaryContent }).containsExactlyInAnyOrder(exp2.summaryContent, exp3.summaryContent)
    }

    private fun content(
        provider: ContentProvider,
        title: String,
        publishedAt: LocalDate = LocalDate.of(2026, 4, 24),
    ): Content =
        Content(
            title = title,
            content = "$title body",
            newsletterName = provider.name,
            originalUrl = "https://example.com/${title.lowercase()}",
            imageUrl = "https://example.com/${title.lowercase()}.png",
            publishedAt = publishedAt,
            contentProvider = provider,
        )

    private fun exposureContent(
        content: Content,
        headline: String,
    ): ExposureContent =
        ExposureContent(
            content = content,
            provocativeKeyword = "Kotlin",
            provocativeHeadline = headline,
            summaryContent = "$headline summary",
        )
}
