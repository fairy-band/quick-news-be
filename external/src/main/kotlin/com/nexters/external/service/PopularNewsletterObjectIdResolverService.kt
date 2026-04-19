package com.nexters.external.service

import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentRepository
import org.springframework.stereotype.Service

@Service
class PopularNewsletterObjectIdResolverService(
    private val contentRepository: ContentRepository,
    private val exposureContentRepository: ExposureContentRepository,
) {
    fun resolveObjectId(rawObjectId: String): PopularNewsletterObjectResolution {
        rawObjectId.toLongOrNull()?.let { numericId ->
            exposureContentRepository.findById(numericId).orElse(null)?.let { exposureContent ->
                return PopularNewsletterObjectResolution(
                    resolvedContentId = exposureContent.content.id,
                    resolvedExposureContentId = exposureContent.id,
                    resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
                )
            }

            contentRepository.findById(numericId).orElse(null)?.let { content ->
                val exposureContent = exposureContentRepository.findByContentId(content.id!!)
                return PopularNewsletterObjectResolution(
                    resolvedContentId = content.id,
                    resolvedExposureContentId = exposureContent?.id,
                    resolutionStatus =
                        if (exposureContent != null) {
                            PopularNewsletterResolutionStatus.RESOLVED
                        } else {
                            PopularNewsletterResolutionStatus.UNRESOLVED
                        },
                )
            }
        }

        contentRepository.findByOriginalUrl(rawObjectId)?.let { content ->
            val exposureContent = exposureContentRepository.findByContentId(content.id!!)
            return PopularNewsletterObjectResolution(
                resolvedContentId = content.id,
                resolvedExposureContentId = exposureContent?.id,
                resolutionStatus =
                    if (exposureContent != null) {
                        PopularNewsletterResolutionStatus.RESOLVED
                    } else {
                        PopularNewsletterResolutionStatus.UNRESOLVED
                    },
            )
        }

        val newsletterSourceMatches =
            contentRepository
                .findByNewsletterSourceId(rawObjectId)
                .sortedWith(compareByDescending<com.nexters.external.entity.Content> { it.publishedAt }.thenByDescending { it.id ?: 0L })

        if (newsletterSourceMatches.isNotEmpty()) {
            val resolvedMatch =
                newsletterSourceMatches.firstOrNull { matchedContent ->
                    matchedContent.id != null && exposureContentRepository.findByContentId(matchedContent.id) != null
                } ?: newsletterSourceMatches.first()

            val exposureContent = exposureContentRepository.findByContentId(resolvedMatch.id!!)
            return PopularNewsletterObjectResolution(
                resolvedContentId = resolvedMatch.id,
                resolvedExposureContentId = exposureContent?.id,
                resolutionStatus =
                    if (exposureContent != null) {
                        PopularNewsletterResolutionStatus.RESOLVED
                    } else {
                        PopularNewsletterResolutionStatus.UNRESOLVED
                    },
            )
        }

        return PopularNewsletterObjectResolution(
            resolutionStatus = PopularNewsletterResolutionStatus.UNRESOLVED,
        )
    }
}

data class PopularNewsletterObjectResolution(
    val resolvedContentId: Long? = null,
    val resolvedExposureContentId: Long? = null,
    val resolutionStatus: PopularNewsletterResolutionStatus,
)
