package com.nexters.external.service

import com.nexters.external.entity.ContentProvider
import com.nexters.external.repository.ContentProviderRepository
import org.springframework.stereotype.Service

@Service
class ContentProviderService(
    private val contentProviderRepository: ContentProviderRepository
) {
    fun findByName(name: String): ContentProvider? = contentProviderRepository.findByName(name)
}
