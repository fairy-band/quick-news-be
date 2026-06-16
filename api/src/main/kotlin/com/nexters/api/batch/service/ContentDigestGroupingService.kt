package com.nexters.api.batch.service

import com.nexters.api.batch.dto.ContentDigestGroupingRequest
import com.nexters.api.batch.dto.ContentDigestGroupingResponse
import com.nexters.api.batch.dto.ContentDigestGroupingSample
import com.nexters.external.entity.Content
import com.nexters.external.repository.ContentRepository
import com.nexters.newsletter.service.NewsletterContentGroupingService
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ContentDigestGroupingService(
    private val jdbcTemplate: JdbcTemplate,
    private val contentRepository: ContentRepository,
    private val entityManager: EntityManager,
) : NewsletterContentGroupingService {
    @Transactional
    override fun groupNewsletterSource(newsletterSourceId: String): List<Content> {
        entityManager.flush()

        groupContents(
            ContentDigestGroupingRequest(
                dryRun = false,
                newsletterSourceIds = setOf(newsletterSourceId),
            ),
        )

        entityManager.clear()
        return contentRepository.findByNewsletterSourceId(newsletterSourceId).sortedBy { content -> content.id }
    }

    @Transactional
    fun groupContents(request: ContentDigestGroupingRequest): ContentDigestGroupingResponse {
        val targetNewsletterNames = request.normalizedNewsletterNames()
        val rows = findTargetContents(request, targetNewsletterNames)
        val sourceGroups = rows.groupBy { it.displayNewsletterName to it.newsletterSourceId }
        val samples = mutableListOf<ContentDigestGroupingSample>()
        var skippedReferencedGroups = 0
        var skippedNonGroupableGroups = 0
        var groupedSourceGroups = 0
        var groupedChunks = 0
        var updatedContents = 0
        var deletedContents = 0
        var untouchedContentsInGroupedSources = 0

        sourceGroups.values.forEach { sourceRows ->
            if (sourceRows.any { row -> row.hasReferences }) {
                skippedReferencedGroups++
                return@forEach
            }

            val chunks = buildGroupableChunks(sourceRows.sortedBy { row -> row.id }, request)
            if (chunks.isEmpty()) {
                skippedNonGroupableGroups++
                return@forEach
            }

            groupedSourceGroups++
            groupedChunks += chunks.size

            val groupedIds = chunks.flatten().map { row -> row.id }.toSet()
            untouchedContentsInGroupedSources += sourceRows.count { row -> row.id !in groupedIds }

            chunks.forEachIndexed { chunkIndex, chunk ->
                val grouped = chunk.toGroupedContent(chunkIndex + 1, chunks.size)
                val keeper = chunk.first()
                val deleteIds = chunk.drop(1).map { row -> row.id }

                samples.addSample(chunk, grouped)

                if (!request.dryRun) {
                    updateKeeperContent(keeper.id, grouped)
                    deleteContents(deleteIds)
                }

                updatedContents++
                deletedContents += deleteIds.size
            }
        }

        return ContentDigestGroupingResponse(
            dryRun = request.dryRun,
            targetNewsletterNames = targetNewsletterNames,
            scannedContents = rows.size,
            scannedSourceGroups = sourceGroups.size,
            skippedReferencedGroups = skippedReferencedGroups,
            skippedNonGroupableGroups = skippedNonGroupableGroups,
            groupedSourceGroups = groupedSourceGroups,
            groupedChunks = groupedChunks,
            updatedContents = updatedContents,
            deletedContents = deletedContents,
            untouchedContentsInGroupedSources = untouchedContentsInGroupedSources,
            estimatedRowReduction = deletedContents,
            samples = samples,
        )
    }

    private fun findTargetContents(
        request: ContentDigestGroupingRequest,
        targetNewsletterNames: Set<String>,
    ): List<ContentDigestRow> {
        val placeholders = targetNewsletterNames.joinToString(", ") { "?" }
        val targetNewsletterSourceIds =
            request.newsletterSourceIds
                ?.filter { id -> id.isNotBlank() }
                ?.toSet()
                ?.takeIf { ids -> ids.isNotEmpty() }
        val sourceIdPlaceholders = targetNewsletterSourceIds?.joinToString(", ") { "?" }
        val params =
            buildList<Any> {
                add("%$GROUPED_CONTENT_MARKER%")
                addAll(targetNewsletterNames)
                add("%$GROUPED_CONTENT_MARKER%")
                targetNewsletterSourceIds?.let { addAll(it) }
                request.minCreatedAt?.let { add(it) }
            }.toTypedArray()
        val newsletterSourceIdCondition =
            if (sourceIdPlaceholders == null) {
                ""
            } else {
                "AND c.newsletter_source_id IN ($sourceIdPlaceholders)"
            }
        val minCreatedAtCondition =
            if (request.minCreatedAt == null) {
                ""
            } else {
                "AND c.created_at >= ?"
            }

        return jdbcTemplate.query(
            """
            SELECT
                c.id,
                c.newsletter_source_id,
                c.newsletter_name,
                c.title,
                c.content,
                c.original_url,
                c.image_url,
                c.published_at,
                c.content_provider_id,
                cp.name AS content_provider_name,
                (
                    SELECT COUNT(*)
                    FROM contents grouped
                    WHERE grouped.newsletter_source_id = c.newsletter_source_id
                        AND grouped.content LIKE ?
                ) AS existing_grouped_count,
                (
                    EXISTS (SELECT 1 FROM summaries s WHERE s.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM exposure_contents ec WHERE ec.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM content_keyword_mappings ckm WHERE ckm.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM content_keyword_match_scores ckms WHERE ckms.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM user_exposed_contents_mapping uec WHERE uec.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM content_generation_attempts cga WHERE cga.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM rss_processing_status rps WHERE rps.content_id = c.id)
                    OR EXISTS (SELECT 1 FROM popular_newsletter_snapshot_items pnsi WHERE pnsi.resolved_content_id = c.id)
                ) AS has_references
            FROM contents c
            LEFT JOIN content_provider cp ON cp.id = c.content_provider_id
            WHERE c.newsletter_source_id IS NOT NULL
                AND c.newsletter_name IN ($placeholders)
                AND c.content NOT LIKE ?
                $newsletterSourceIdCondition
                $minCreatedAtCondition
            ORDER BY c.newsletter_name, c.newsletter_source_id, c.id
            """.trimIndent(),
            params,
        ) { rs, _ -> rs.toContentDigestRow() }
    }

    private fun buildGroupableChunks(
        rows: List<ContentDigestRow>,
        request: ContentDigestGroupingRequest,
    ): List<List<ContentDigestRow>> {
        if (rows.count { row -> row.content.length < request.minShortContentLength } < request.minShortItems) {
            return emptyList()
        }

        val fullGroup = rows.toGroupedContent(1, 1)
        if (fullGroup.content.length in request.minGroupContentLength..request.maxGroupContentLength) {
            return listOf(rows)
        }

        if (fullGroup.content.length < request.minGroupContentLength) {
            return emptyList()
        }

        val chunks = mutableListOf<List<ContentDigestRow>>()
        var current = mutableListOf<ContentDigestRow>()

        rows.forEach { row ->
            val candidate = current + row
            val candidateLength = candidate.toGroupedContent(chunks.size + 1, 1).content.length

            if (current.isNotEmpty() && candidateLength > request.maxGroupContentLength) {
                chunks += current
                current = mutableListOf(row)
            } else {
                current.add(row)
            }
        }

        if (current.isNotEmpty()) {
            chunks += current
        }

        return chunks
            .mapIndexed { index, chunk -> chunk to chunk.toGroupedContent(index + 1, chunks.size) }
            .filter { (chunk, grouped) ->
                chunk.size >= request.minShortItems &&
                    grouped.content.length in request.minGroupContentLength..request.maxGroupContentLength
            }.map { (chunk, _) -> chunk }
    }

    private fun List<ContentDigestRow>.toGroupedContent(
        chunkNumber: Int,
        chunkCount: Int,
    ): GroupedContent {
        val first = first()
        val title = buildGroupTitle(first, chunkNumber, chunkCount)

        return GroupedContent(
            title = title,
            content = buildGroupContent(title),
            originalUrl = first.originalUrl,
            imageUrl = firstNotNullOfOrNull { row -> row.imageUrl },
        )
    }

    private fun List<ContentDigestRow>.buildGroupContent(title: String): String =
        buildString {
            append(title).append("입니다. 같은 뉴스레터에서 소개된 짧은 항목들을 한 번에 볼 수 있도록 묶었습니다.")
            append("\n\n")

            this@buildGroupContent.forEachIndexed { index, row ->
                append(index + 1).append(". ").append(row.title.cleanInlineText()).append("\n")

                val description = row.content.cleanGroupedDescription()
                if (description.isNotBlank()) {
                    append(description).append("\n")
                }

                append("Link: ").append(row.originalUrl)

                if (index < this@buildGroupContent.lastIndex) {
                    append("\n\n")
                }
            }
        }

    private fun buildGroupTitle(
        first: ContentDigestRow,
        chunkNumber: Int,
        chunkCount: Int,
    ): String {
        val issue = first.extractIssueNumber()
        val newsletterName = first.displayNewsletterName
        val baseTitle =
            if (!issue.isNullOrBlank()) {
                "$newsletterName Issue #$issue 모음"
            } else {
                "${first.publishedAt.format(KOREAN_DATE_FORMATTER)} $newsletterName 모음"
            }

        return if (chunkCount > 1) {
            if (first.existingGroupedCount > 0) {
                "$baseTitle (추가 $chunkNumber/$chunkCount)"
            } else {
                "$baseTitle ($chunkNumber/$chunkCount)"
            }
        } else if (first.existingGroupedCount > 0) {
            "$baseTitle (추가)"
        } else {
            baseTitle
        }
    }

    private fun ContentDigestRow.extractIssueNumber(): String? {
        if (newsletterName == "React Status") return null

        return ISSUE_REGEX
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.knownIssueNumber()
    }

    private fun updateKeeperContent(
        keeperId: Long,
        grouped: GroupedContent,
    ) {
        jdbcTemplate.update(
            """
            UPDATE contents
            SET title = ?,
                content = ?,
                original_url = ?,
                image_url = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """.trimIndent(),
            grouped.title,
            grouped.content,
            grouped.originalUrl,
            grouped.imageUrl,
            keeperId,
        )
    }

    private fun deleteContents(ids: List<Long>) {
        if (ids.isEmpty()) return

        val placeholders = ids.joinToString(", ") { "?" }
        jdbcTemplate.update(
            "DELETE FROM contents WHERE id IN ($placeholders)",
            *ids.toTypedArray(),
        )
    }

    private fun MutableList<ContentDigestGroupingSample>.addSample(
        chunk: List<ContentDigestRow>,
        grouped: GroupedContent,
    ) {
        if (size >= SAMPLE_LIMIT) return

        val first = chunk.first()
        this +=
            ContentDigestGroupingSample(
                newsletterName = first.displayNewsletterName,
                newsletterSourceId = first.newsletterSourceId,
                title = grouped.title,
                contentIds = chunk.map { row -> row.id },
                groupedContentLength = grouped.content.length,
            )
    }

    private fun ContentDigestGroupingRequest.normalizedNewsletterNames(): Set<String> =
        newsletterNames
            ?.filter { name -> name.isNotBlank() }
            ?.toSet()
            ?.takeIf { names -> names.isNotEmpty() }
            ?: DEFAULT_TARGET_NEWSLETTER_NAMES

    private fun ResultSet.toContentDigestRow(): ContentDigestRow =
        ContentDigestRow(
            id = getLong("id"),
            newsletterSourceId = getString("newsletter_source_id"),
            newsletterName = getString("newsletter_name"),
            title = getString("title"),
            content = getString("content"),
            originalUrl = getString("original_url"),
            imageUrl = getString("image_url"),
            publishedAt = getDate("published_at").toLocalDate(),
            contentProviderId = getLong("content_provider_id").takeUnless { wasNull() },
            contentProviderName = getString("content_provider_name"),
            existingGroupedCount = getInt("existing_grouped_count"),
            hasReferences = getBoolean("has_references"),
        )

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ")
            .trim()

    private fun String.cleanGroupedDescription(): String =
        replace(ISSUE_CONTENT_PREFIX_REGEX, "")
            .cleanInlineText()

    private fun String.knownIssueNumber(): String? {
        val value = trim().removePrefix("#")
        if (value.equals("Unknown", ignoreCase = true) || value.equals("Unknown date", ignoreCase = true)) {
            return null
        }

        val numericValue = value.toIntOrNull()
        if (numericValue != null && numericValue > MAX_REASONABLE_ISSUE_NUMBER) {
            return null
        }

        return value.takeIf { it.isNotBlank() }
    }

    private data class ContentDigestRow(
        val id: Long,
        val newsletterSourceId: String,
        val newsletterName: String,
        val title: String,
        val content: String,
        val originalUrl: String,
        val imageUrl: String?,
        val publishedAt: LocalDate,
        val contentProviderId: Long?,
        val contentProviderName: String?,
        val existingGroupedCount: Int,
        val hasReferences: Boolean,
    ) {
        val displayNewsletterName: String
            get() = contentProviderName?.takeIf { name -> name.isNotBlank() } ?: newsletterName
    }

    private data class GroupedContent(
        val title: String,
        val content: String,
        val originalUrl: String,
        val imageUrl: String?,
    )

    companion object {
        private const val SAMPLE_LIMIT = 20

        private val DEFAULT_TARGET_NEWSLETTER_NAMES =
            setOf(
                "ITWorld Korea",
                "TLDR",
                "Android Weekly",
                "Awesome Android Newsletter",
                "Awesome iOS Weekly",
                "Awesome Java Newsletter",
                "Awesome Java Weekly",
                "Awesome Kotlin Weekly",
                "Java Weekly",
                "Python Weekly",
                "VS Code Email",
                "Frontend Focus",
                "Node Weekly",
                "Go Weekly",
                "Postgres Weekly",
                "GeekNews Weekly",
                "React Status",
                "Kotlin Weekly",
                "JavaScript Weekly",
            )

        private val KOREAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
        private const val GROUPED_CONTENT_MARKER = "같은 뉴스레터에서 소개된 짧은 항목들을 한 번에 볼 수 있도록 묶었습니다."
        private const val MAX_REASONABLE_ISSUE_NUMBER = 10_000
        private val ISSUE_REGEX = Regex("""Issue\s+#?([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
        private val ISSUE_CONTENT_PREFIX_REGEX =
            Regex("""^(?:\[[^]]+]\s*)?Issue\s+#?\S+\s*(?:\([^)]+\))?:\s*""", RegexOption.IGNORE_CASE)
    }
}
