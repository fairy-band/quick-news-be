package com.nexters.external.service

import com.nexters.external.enums.PopularNewsletterResolutionStatus
import com.nexters.external.repository.ContentLookupRow
import com.nexters.external.repository.ContentRepository
import com.nexters.external.repository.ExposureContentLookupRow
import com.nexters.external.repository.ExposureContentRepository
import org.springframework.stereotype.Service

@Service
class PopularNewsletterObjectIdResolverService(
    private val contentRepository: ContentRepository,
    private val exposureContentRepository: ExposureContentRepository,
) {
    fun resolveObjectId(rawObjectId: String): PopularNewsletterObjectResolution =
        resolveObjectIds(listOf(rawObjectId))[rawObjectId] ?: unresolvedResolution()

    fun resolveObjectIds(rawObjectIds: List<String>): Map<String, PopularNewsletterObjectResolution> {
        if (rawObjectIds.isEmpty()) {
            return emptyMap()
        }

        val uniqueRawObjectIds = rawObjectIds.distinct()
        val resolvedByRawObjectId = mutableMapOf<String, PopularNewsletterObjectResolution>()
        val numericIdByRawObjectId =
            uniqueRawObjectIds
                .mapNotNull { rawObjectId ->
                    rawObjectId.toLongOrNull()?.let { numericId ->
                        rawObjectId to numericId
                    }
                }.toMap()

        resolveByExposureContentId(numericIdByRawObjectId, resolvedByRawObjectId)
        resolveByContentId(numericIdByRawObjectId, resolvedByRawObjectId)
        resolveByOriginalUrl(uniqueRawObjectIds, resolvedByRawObjectId)
        resolveByNewsletterSourceId(uniqueRawObjectIds, resolvedByRawObjectId)

        return uniqueRawObjectIds.associateWith { rawObjectId ->
            resolvedByRawObjectId[rawObjectId] ?: unresolvedResolution()
        }
    }

    private fun resolveByExposureContentId(
        numericIdByRawObjectId: Map<String, Long>,
        resolvedByRawObjectId: MutableMap<String, PopularNewsletterObjectResolution>,
    ) {
        val numericIds = numericIdByRawObjectId.values.toSet()
        if (numericIds.isEmpty()) {
            return
        }

        val exposureContentLookupById =
            exposureContentRepository
                .findLookupRowsByIds(numericIds)
                .associateBy { it.id }

        numericIdByRawObjectId.forEach { (rawObjectId, numericId) ->
            exposureContentLookupById[numericId]?.let { exposureContent ->
                resolvedByRawObjectId[rawObjectId] =
                    PopularNewsletterObjectResolution(
                        resolvedContentId = exposureContent.contentId,
                        resolvedExposureContentId = exposureContent.id,
                        resolutionStatus = PopularNewsletterResolutionStatus.RESOLVED,
                    )
            }
        }
    }

    private fun resolveByContentId(
        numericIdByRawObjectId: Map<String, Long>,
        resolvedByRawObjectId: MutableMap<String, PopularNewsletterObjectResolution>,
    ) {
        val unresolvedNumericIdByRawObjectId =
            numericIdByRawObjectId.filterKeys { rawObjectId ->
                !resolvedByRawObjectId.containsKey(rawObjectId)
            }
        if (unresolvedNumericIdByRawObjectId.isEmpty()) {
            return
        }

        val contentLookupById =
            contentRepository
                .findLookupRowsByIds(unresolvedNumericIdByRawObjectId.values.toSet())
                .associateBy { it.id }
        val exposureLookupByContentId =
            findExposureLookupByContentId(contentLookupById.keys)

        unresolvedNumericIdByRawObjectId.forEach { (rawObjectId, numericId) ->
            contentLookupById[numericId]?.let { content ->
                resolvedByRawObjectId[rawObjectId] = toResolution(content.id, exposureLookupByContentId[content.id]?.id)
            }
        }
    }

    private fun resolveByOriginalUrl(
        rawObjectIds: List<String>,
        resolvedByRawObjectId: MutableMap<String, PopularNewsletterObjectResolution>,
    ) {
        val unresolvedRawObjectIds = rawObjectIds.filterNot { resolvedByRawObjectId.containsKey(it) }
        if (unresolvedRawObjectIds.isEmpty()) {
            return
        }

        val preferredContentByOriginalUrl =
            selectPreferredRowsByKey(
                rows = contentRepository.findLookupRowsByOriginalUrls(unresolvedRawObjectIds.toSet()),
                keySelector = { it.originalUrl },
            )
        val exposureLookupByContentId =
            findExposureLookupByContentId(preferredContentByOriginalUrl.values.map { it.id }.toSet())

        unresolvedRawObjectIds.forEach { rawObjectId ->
            preferredContentByOriginalUrl[rawObjectId]?.let { content ->
                resolvedByRawObjectId[rawObjectId] = toResolution(content.id, exposureLookupByContentId[content.id]?.id)
            }
        }
    }

    private fun resolveByNewsletterSourceId(
        rawObjectIds: List<String>,
        resolvedByRawObjectId: MutableMap<String, PopularNewsletterObjectResolution>,
    ) {
        val unresolvedRawObjectIds = rawObjectIds.filterNot { resolvedByRawObjectId.containsKey(it) }
        if (unresolvedRawObjectIds.isEmpty()) {
            return
        }

        val sourceMatches = contentRepository.findLookupRowsByNewsletterSourceIds(unresolvedRawObjectIds.toSet())
        if (sourceMatches.isEmpty()) {
            return
        }

        val exposureLookupByContentId =
            findExposureLookupByContentId(sourceMatches.map { it.id }.toSet())
        val preferredContentByNewsletterSourceId =
            selectPreferredRowsByKey(
                rows = sourceMatches,
                keySelector = { it.newsletterSourceId ?: return@selectPreferredRowsByKey null },
                exposureLookupByContentId = exposureLookupByContentId,
            )

        unresolvedRawObjectIds.forEach { rawObjectId ->
            preferredContentByNewsletterSourceId[rawObjectId]?.let { content ->
                resolvedByRawObjectId[rawObjectId] = toResolution(content.id, exposureLookupByContentId[content.id]?.id)
            }
        }
    }

    private fun findExposureLookupByContentId(contentIds: Collection<Long>): Map<Long, ExposureContentLookupRow> {
        if (contentIds.isEmpty()) {
            return emptyMap()
        }

        return exposureContentRepository
            .findLookupRowsByContentIds(contentIds)
            .associateBy { it.contentId }
    }

    private fun selectPreferredRowsByKey(
        rows: List<ContentLookupRow>,
        keySelector: (ContentLookupRow) -> String?,
        exposureLookupByContentId: Map<Long, ExposureContentLookupRow> = emptyMap(),
    ): Map<String, ContentLookupRow> =
        rows
            .groupBy { row -> keySelector(row) }
            .mapNotNull { (key, candidates) ->
                key?.let {
                    val preferredCandidate =
                        candidates.firstOrNull { candidate -> exposureLookupByContentId.containsKey(candidate.id) }
                            ?: candidates.firstOrNull()
                    preferredCandidate?.let { candidate -> key to candidate }
                }
            }.toMap()

    private fun toResolution(
        contentId: Long,
        exposureContentId: Long?,
    ): PopularNewsletterObjectResolution =
        PopularNewsletterObjectResolution(
            resolvedContentId = contentId,
            resolvedExposureContentId = exposureContentId,
            resolutionStatus =
                if (exposureContentId != null) {
                    PopularNewsletterResolutionStatus.RESOLVED
                } else {
                    PopularNewsletterResolutionStatus.UNRESOLVED
                },
        )

    private fun unresolvedResolution(): PopularNewsletterObjectResolution =
        PopularNewsletterObjectResolution(
            resolutionStatus = PopularNewsletterResolutionStatus.UNRESOLVED,
        )
}

data class PopularNewsletterObjectResolution(
    val resolvedContentId: Long? = null,
    val resolvedExposureContentId: Long? = null,
    val resolutionStatus: PopularNewsletterResolutionStatus,
)
