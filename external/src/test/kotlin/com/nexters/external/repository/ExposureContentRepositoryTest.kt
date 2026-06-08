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
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
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
        val rows = repository.findExploreRows(PageRequest.of(0, 2, byIdDesc))
        val rowsAfterNewest =
            repository.findExploreRowsAfter(
                lastSeenOffset = secondExposureContent.id!!,
                pageable = PageRequest.of(0, 2, byIdDesc),
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
    }

    @Test
    fun `findNotExposedByReservedKeywordIds should return non-exposed exposure contents that match given keywords`() {
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

        // Expose content1 to user
        entityManager.persist(UserExposedContentMapping(userId = user.id!!, contentId = content1.id!!))

        entityManager.flush()
        entityManager.clear()

        // When
        val result =
            repository.findNotExposedByReservedKeywordIds(
                userId = user.id!!,
                reservedKeywordIds = listOf(keyword1.id!!, keyword2.id!!, keyword3.id!!)
            )
        val candidates =
            repository.findNotExposedRecommendationCandidatesByReservedKeywordIds(
                userId = user.id!!,
                reservedKeywordIds = listOf(keyword1.id!!, keyword2.id!!, keyword3.id!!)
            )

        // Then
        // Should only contain exp2 and exp3 (since exp1 is already exposed)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder(exp2.id, exp3.id)
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
    ): Content =
        Content(
            title = title,
            content = "$title body",
            newsletterName = provider.name,
            originalUrl = "https://example.com/${title.lowercase()}",
            imageUrl = "https://example.com/${title.lowercase()}.png",
            publishedAt = LocalDate.of(2026, 4, 24),
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
