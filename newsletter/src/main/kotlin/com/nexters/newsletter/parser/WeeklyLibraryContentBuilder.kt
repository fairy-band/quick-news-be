package com.nexters.newsletter.parser

import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields
import java.util.Locale

internal object WeeklyLibraryContentBuilder {
    fun groupSections(
        contents: List<MailContent>,
        issueNumber: String?,
        issueDate: String?,
        sections: Set<String>,
        issueLink: String? = null,
    ): List<MailContent> =
        group(contents, issueNumber, issueDate, issueLink) { content ->
            content.section?.let { section ->
                sections.any { it.equals(section, ignoreCase = true) }
            } ?: false
        }

    fun group(
        contents: List<MailContent>,
        issueNumber: String?,
        issueDate: String?,
        issueLink: String? = null,
        isLibraryItem: (MailContent) -> Boolean,
    ): List<MailContent> {
        val libraryItems = contents.filter(isLibraryItem)
        if (libraryItems.isEmpty()) return contents

        val groupedContent = createGroupedContent(libraryItems, issueNumber, issueDate, issueLink)
        val grouped = mutableListOf<MailContent>()
        var inserted = false

        contents.forEach { content ->
            if (isLibraryItem(content)) {
                if (!inserted) {
                    grouped += groupedContent
                    inserted = true
                }
            } else {
                grouped += content
            }
        }

        return grouped
    }

    private fun createGroupedContent(
        items: List<MailContent>,
        issueNumber: String?,
        issueDate: String?,
        issueLink: String?,
    ): MailContent {
        val title = buildTitle(issueNumber, issueDate)

        return MailContent(
            title = title,
            content = buildContent(title, issueNumber, items),
            link = issueLink?.takeIf { it.isNotBlank() } ?: items.first().link,
            section = SECTION_LIBRARIES,
            imageUrl = items.firstNotNullOfOrNull { it.imageUrl },
        )
    }

    private fun buildTitle(
        issueNumber: String?,
        issueDate: String?,
    ): String {
        parseDate(issueDate)?.let { date ->
            val weekBasedYear = date.get(WEEK_FIELDS.weekBasedYear())
            val week = date.get(WEEK_FIELDS.weekOfWeekBasedYear())
            return "${weekBasedYear}년 ${week}주의 라이브러리"
        }

        val knownIssueNumber = issueNumber.knownValue()
        return if (knownIssueNumber != null) {
            "Issue #${knownIssueNumber}의 라이브러리"
        } else {
            "이번 주의 라이브러리"
        }
    }

    private fun buildContent(
        title: String,
        issueNumber: String?,
        items: List<MailContent>,
    ): String =
        buildString {
            append(title).append(" 모음입니다.")

            issueNumber.knownValue()?.let { number ->
                append(" Issue #").append(number).append("에서 소개된 항목들을 정리했습니다.")
            }

            append("\n\n")

            items.forEachIndexed { index, item ->
                append(index + 1).append(". ").append(item.title).append("\n")

                val description = item.content.cleanLibraryDescription()
                if (description.isNotBlank()) {
                    append(description).append("\n")
                }

                append("Link: ").append(item.link)

                if (index < items.lastIndex) {
                    append("\n\n")
                }
            }
        }

    private fun parseDate(dateText: String?): LocalDate? {
        val normalized = dateText?.normalizeDateText() ?: return null

        return DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
            try {
                LocalDate.parse(normalized, formatter)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun String.normalizeDateText(): String {
        val compact = replace(Regex("\\s+"), " ").trim()
        val ordinalMatch = ORDINAL_DATE_REGEX.matchEntire(compact)

        return if (ordinalMatch != null) {
            val (day, month, year) = ordinalMatch.destructured
            "$day $month $year"
        } else {
            compact
        }
    }

    private fun String.cleanLibraryDescription(): String =
        replace(ISSUE_CONTENT_PREFIX_REGEX, "")
            .trim()

    private fun String?.knownValue(): String? =
        this
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it.equals("Unknown", ignoreCase = true) }
            ?.takeUnless { it.equals("Unknown date", ignoreCase = true) }

    private const val SECTION_LIBRARIES = "Libraries"

    private val WEEK_FIELDS = WeekFields.ISO
    private val ORDINAL_DATE_REGEX = Regex("""(\d{1,2})(?:st|nd|rd|th)\s+of\s+([A-Za-z]+)\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val ISSUE_CONTENT_PREFIX_REGEX =
        Regex("""^(?:\[[^]]+]\s*)?Issue\s+#?\S+\s*(?:\([^)]+\))?:\s*""", RegexOption.IGNORE_CASE)

    private val DATE_FORMATTERS =
        listOf(
            "MMMM d, uuuu",
            "MMMM dd, uuuu",
            "MMM d, uuuu",
            "MMM dd, uuuu",
            "d MMMM uuuu",
            "d MMM uuuu",
        ).map { pattern ->
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH)
        }
}
