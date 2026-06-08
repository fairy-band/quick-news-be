package com.nexters.external.repository

import com.nexters.external.entity.Content
import com.nexters.external.entity.User
import com.nexters.external.entity.UserExposedContentMapping
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
class UserExposedContentMappingRepositoryTest {
    @Autowired
    private lateinit var repository: UserExposedContentMappingRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `date range queries should use active and deleted flags without date function semantics`() {
        val date = LocalDate.of(2026, 6, 8)
        val startAt = date.atStartOfDay()
        val endAt = date.plusDays(1).atStartOfDay()
        val user = entityManager.persist(User(deviceToken = "device-token"))
        val otherUser = entityManager.persist(User(deviceToken = "other-device-token"))
        val activeContent = entityManager.persist(content("Active"))
        val deletedContent = entityManager.persist(content("Deleted"))
        val previousDayContent = entityManager.persist(content("Previous"))
        val otherUserContent = entityManager.persist(content("Other"))

        entityManager.persist(
            UserExposedContentMapping(
                userId = user.id!!,
                contentId = activeContent.id!!,
                createdAt = LocalDateTime.of(2026, 6, 8, 10, 0),
                deleted = false,
            ),
        )
        entityManager.persist(
            UserExposedContentMapping(
                userId = user.id!!,
                contentId = deletedContent.id!!,
                createdAt = LocalDateTime.of(2026, 6, 8, 11, 0),
                deleted = true,
            ),
        )
        entityManager.persist(
            UserExposedContentMapping(
                userId = user.id!!,
                contentId = previousDayContent.id!!,
                createdAt = LocalDateTime.of(2026, 6, 7, 23, 59),
                deleted = true,
            ),
        )
        entityManager.persist(
            UserExposedContentMapping(
                userId = otherUser.id!!,
                contentId = otherUserContent.id!!,
                createdAt = LocalDateTime.of(2026, 6, 8, 12, 0),
                deleted = true,
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val activeMappings =
            repository.findActiveByUserIdAndCreatedAtRange(
                userId = user.id!!,
                startAt = startAt,
                endAt = endAt,
            )
        val hasDeletedToday =
            repository.existsDeletedByUserIdAndCreatedAtRange(
                userId = user.id!!,
                startAt = startAt,
                endAt = endAt,
            )

        assertThat(activeMappings.map { it.contentId }).containsExactly(activeContent.id)
        assertThat(hasDeletedToday).isTrue()

        val updatedCount =
            repository.markActiveAsDeletedByUserIdAndCreatedAtRange(
                userId = user.id!!,
                startAt = startAt,
                endAt = endAt,
            )
        entityManager.flush()
        entityManager.clear()

        assertThat(updatedCount).isEqualTo(1)
        assertThat(
            repository.findActiveByUserIdAndCreatedAtRange(
                userId = user.id!!,
                startAt = startAt,
                endAt = endAt,
            ),
        ).isEmpty()
    }

    private fun content(title: String): Content =
        Content(
            title = title,
            content = "$title body",
            newsletterName = "Newsletter",
            originalUrl = "https://example.com/${title.lowercase()}",
            publishedAt = LocalDate.of(2026, 6, 8),
        )
}
