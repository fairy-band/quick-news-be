package com.nexters.external.service

import com.nexters.external.entity.ContentProviderRequest
import com.nexters.external.repository.ContentProviderRequestRepository
import org.springframework.stereotype.Service

@Service
class ContentProviderRequestService(
    private val contentProviderRequestRepository: ContentProviderRequestRepository,
) {
    fun createRequest(
        contentProviderName: String,
        channel: String,
        requestCategory: String,
        relatedTo: String,
        reason: String?,
    ): Long {
        val contentProviderRequest =
            ContentProviderRequest(
                contentProviderName = contentProviderName,
                channel = channel,
                requestCategory = requestCategory,
                relatedTo = relatedTo,
                reason = reason,
            )
        val saved = contentProviderRequestRepository.save(contentProviderRequest)
        return saved.id ?: throw IllegalStateException("ContentProviderRequest ID should not be null after saving.")
    }
}
