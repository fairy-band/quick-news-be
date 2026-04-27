package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.ExposureContent
import com.nexters.external.enums.ContentProviderType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
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

        val rows = repository.findExploreRows(PageRequest.of(0, 2))
        val rowsAfterNewest =
            repository.findExploreRowsAfter(
                lastSeenOffset = secondExposureContent.id!!,
                pageable = PageRequest.of(0, 2),
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
