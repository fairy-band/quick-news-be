package com.nexters.newsletter.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WeeklyLibraryContentBuilderTest {
    @Test
    fun `group should prefer issue newsletter link over first item link when issueLink is missing`() {
        val contents =
            listOf(
                mailContent(
                    title = "fx2048 - Play 2048 offline on desktop",
                    link = "https://brunoborges.github.io/fx2048",
                    section = "Popular projects",
                ),
                mailContent(
                    title = "Java Weekly Issue #522",
                    link = "https://java.libhunt.com/newsletter/522",
                    section = "Popular projects",
                ),
            )

        val grouped =
            WeeklyLibraryContentBuilder.groupSections(
                contents = contents,
                issueNumber = "522",
                issueDate = null,
                sections = setOf("Popular projects"),
            )

        assertThat(grouped).hasSize(1)
        assertThat(grouped.single().link).isEqualTo("https://java.libhunt.com/newsletter/522")
    }

    @Test
    fun `group should keep explicit issueLink when provided`() {
        val contents =
            listOf(
                mailContent(
                    title = "Project",
                    link = "https://example.com/project",
                    section = "Libraries",
                ),
                mailContent(
                    title = "Issue",
                    link = "https://example.com/newsletter/522",
                    section = "Libraries",
                ),
            )

        val grouped =
            WeeklyLibraryContentBuilder.groupSections(
                contents = contents,
                issueNumber = "522",
                issueDate = null,
                sections = setOf("Libraries"),
                issueLink = "https://weekly.example.com/issues/522",
            )

        assertThat(grouped.single().link).isEqualTo("https://weekly.example.com/issues/522")
    }

    private fun mailContent(
        title: String,
        link: String,
        section: String,
    ): MailContent =
        MailContent(
            title = title,
            content = title,
            link = link,
            section = section,
        )
}
