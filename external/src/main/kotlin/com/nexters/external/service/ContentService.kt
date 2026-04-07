package com.nexters.external.service

import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.repository.ContentProviderRepository
import com.nexters.external.repository.ContentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class ContentService(
    private val contentRepository: ContentRepository,
    private val contentProviderRepository: ContentProviderRepository,
) {
    fun save(content: Content): Content = contentRepository.save(content)

    @Transactional
    fun createContent(
        title: String,
        content: String,
        contentProviderName: String,
        originalUrl: String,
        publishedAt: LocalDate,
        newsletterSourceId: String? = null,
        imageUrl: String? = null,
    ): Content {
        val contentProvider =
            contentProviderName.let { name ->
                val existing = contentProviderRepository.findByNameWithLock(name)
                existing ?: createContentProvider(name)
            }

        val newContent =
            Content(
                title = title,
                content = content,
                newsletterName = contentProviderName,
                originalUrl = originalUrl,
                imageUrl = imageUrl,
                publishedAt = publishedAt,
                newsletterSourceId = newsletterSourceId,
                contentProvider = contentProvider,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        return contentRepository.save(newContent)
    }

    private fun createContentProvider(name: String): ContentProvider {
        val newProvider =
            ContentProvider(
                name = name,
                channel = name,
                language = "ko",
                type = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
        return contentProviderRepository.save(newProvider)
    }
}
