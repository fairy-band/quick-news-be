package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class KotlinWeeklyParserTest {
    private val sut = KotlinWeeklyParser()

    @Test
    fun `isTarget should return true for Kotlin Weekly emails`() {
        assertTrue(sut.isTarget("Kotlin Weekly <mailinglist@kotlinweekly.net>"))
        assertTrue(sut.isTarget("newsletter@kotlinweekly.net"))
        assertTrue(sut.isTarget("Kotlin Weekly"))
    }

    @Test
    fun `isTarget should return false for non-Kotlin Weekly emails`() {
        assertFalse(sut.isTarget("Some Other Newsletter <info@example.com>"))
        assertFalse(sut.isTarget("random@example.com"))
    }

    @Test
    @Disabled("확인 필요")
    fun `parse should extract articles from Kotlin Weekly newsletter`() {
        val sampleEmail =
            """
            Content-Type: text/plain; charset="utf-8"; format="fixed"
            Content-Transfer-Encoding: quoted-printable

            ** ISSUE #468
            ------------------------------------------------------------
            20th of July 2025

            Announcements
            Develocity Plugin for IntelliJ (https://plugins.jetbrains.com/plugin/27471-=
            develocity/)
            The Gradle team has released the Develocity plugin for IntelliJ IDEA and An=
            droid Studio, which provides live build time information directly in the ID=
            E. Check it out!
            plugins.jetbrains.com

            Building Better Agents: What=E2=80=99s New in Koog 0.3.0 (https://blog.jetb=
            rains.com/ai/2025/07/building-better-agents-what-s-new-in-koog-0-3-0/)
            JetBrains has just released Koog 0.3.0, which comes with many updates that =
            make building, running, and managing intelligent agents easier.
            blog.jetbrains.com

            Breaking to Build: Fuzzing the Kotlin Compiler (https://blog.jetbrains.com/=
            research/2025/07/fuzzing-the-kotlin-compiler/)
            Katie Fraser outlines JetBrains=E2=80=99 evolutionary generative fuzzing, d=
            eveloped with TU Delft, to expose and fix subtle bugs in the Kotlin compile=
            r, including K2 regressions.
            blog.jetbrains.com

            Anvil Moves to Maintenance Mode (https://github.com/square/anvil/issues/114=
            9)
            Anvil is moving into mainteinance mode and endorsing Metro as its successor=
            . Read more about it to know what this means for you, if you are an Anvil u=
            ser.
            github.com

            Ktor 3.2.2 Is Now Available (https://blog.jetbrains.com/kotlin/2025/07/ktor=
            -3-2-0-is-now-available-2/)
            The Ktor 3.2.2 patch release brings a critical fix for Android D8 compatibi=
            lity, along with some minor enhancements and bug fixes. Check more of the g=
            oodies included here.
            blog.jetbrains.com

            Articles
            Flow Marbles (https://terrakok.github.io/FlowMarbles/)
            The following website provides interactive diagrams of Kotlinx.coroutines F=
            low, a nice way to visualize them.
            terrakok.github.io

            How to turn callback functions into suspend functions or Flow (https://kt.a=
            cademy/article/interop-callbacks-to-coroutines)
            Marcin Moskala explains how to convert callback-based APIs into suspend fun=
            ctions or Flows to make them coroutine-friendly and idiomatic in Kotlin.
            kt.academy

            Exploring PausableComposition internals in Jetpack Compose (https://blog.sh=
            reyaspatil.dev/exploring-pausablecomposition-internals-in-jetpack-compose)
            Elian van Cutsem explores Kotlin DSLs as an elegant alternative to annotati=
            on processing, enabling compile-time code generation with improved readabil=
            ity and reduced complexity.
            blog.shreyaspatil.dev

            Contract Test (https://zalas.pl/contract-test/)
            Jakub Zalas presents the Contract Test pattern, adapted from xUnit Test Pat=
            terns, as a structured way to verify implementations of abstractions that i=
            nterface with external systems.
            zalas.pl

            Kotlin Clean Code Rearranger (https://plugins.jetbrains.com/plugin/27537-ko=
            tlin-clean-code-rearranger)
            Marco Plasmati has published an Intellij IDEA plugin tha rearranges Kotlin =
            functions according to the Step-Down rule.
            plugins.jetbrains.com

            Sponsored
            Product for Engineers newsletter ( https://newsletter.posthog.com?r=3D5yje7=
            p)
            Learn to build better products, not just better code. Learn how to talk to =
            users, build features they love, and find product market fit. Subscribe for=
             free.
            newsletter.posthog.com

            Android
            How to Input Unicode Characters in Maestro Android Tests: A Complete Workar=
            ound Guide (https://blog.droidchef.dev/how-to-input-unicode-characters-in-m=
            aestro-android-tests-a-complete-workaround-guide/)
            Ishan Khanna shares a complete workaround to enable Unicode input in Maestr=
            o Android tests using ADB Keyboard, a local HTTP server, and JavaScript int=
            egration.
            blog.droidchef.dev

            Podcast
            Android MCP SDK with Jason Pearson (https://thebakery.dev/99/)
            Nico Corti speaks with Jason Pearson about the Android MCP SDK, a new libra=
            ry that allow Android developers to build apps that can communicate with AI=
            models more effectively.
            thebakery.dev

            Conferences
            Unlock Kotlin Flow: Free Live Webinar =E2=80=93 July 22 (https://webinar.kt=
            .academy/mastering-kotlin-flow-07)
            Go beyond basics! Learn how Kotlin Flow handles values, errors & completion=
             downstream and how requests travel upstream. Understand core operators & c=
            oncurrency syncing. 2 time zones, live Q&A.
            webinar.kt.academy (https://webinar.kt.academy/mastering-kotlin-flow-07)

            Libraries
            Liquid Glass (https://github.com/Kyant0/AndroidLiquidGlass)
            Library providing Apple's Liquid Glass effect for Android Jetpack Compose.
            github.com

            ScribeSwan (https://gitlab.com/islandoftex/libraries/scribeswan/)
            ScribeSwan is a Kotlin Multiplatform library that provides a DSL (domain-sp=
            ecific language) for creating manpages in the troff format used by the Unix=
             man command.
            gitlab.com
            """.trimIndent()

        // when
        val result = sut.parse(sampleEmail)

        // then
        assertEquals(14, result.size, "Expected 16 articles, but got ${result.size}")

        // Verify articles irrespective of order
        verifyArticle(
            result[0],
            "Develocity Plugin for IntelliJ",
            "https://plugins.jetbrains.com/plugin/27471-=develocity/",
            "Announcements",
            "The Gradle team has released",
        )

        verifyArticle(
            result[1],
            "Building Better Agents: What=E2=80=99s New in Koog 0.3.0",
            "https://blog.jetb=rains.com/ai/2025/07/building-better-agents-what-s-new-in-koog-0-3-0/",
            "Announcements",
            "JetBrains has just released Koog 0.3.0",
        )

        verifyArticle(
            result[2],
            "Breaking to Build: Fuzzing the Kotlin Compiler",
            "https://blog.jetbrains.com/=research/2025/07/fuzzing-the-kotlin-compiler/",
            "Announcements",
            "evolutionary generative fuzzing",
        )

        verifyArticle(
            result[3],
            "Anvil Moves to Maintenance Mode",
            "https://github.com/square/anvil/issues/114=9",
            "Announcements",
            "mainteinance mode",
        )

        verifyArticle(
            result[4],
            "Ktor 3.2.2 Is Now Available",
            "https://blog.jetbrains.com/kotlin/2025/07/ktor=-3-2-0-is-now-available-2/",
            "Announcements",
            "critical fix for Android D8",
        )

        verifyArticle(
            result[5],
            "Flow Marbles",
            "https://terrakok.github.io/FlowMarbles/",
            "Articles",
            "interactive diagrams",
        )

        verifyArticle(
            result[6],
            "How to turn callback functions into suspend functions or Flow",
            "https://kt.a=cademy/article/interop-callbacks-to-coroutines",
            "Articles",
            "convert callback-based APIs",
        )

        verifyArticle(
            result[7],
            "Exploring PausableComposition internals in Jetpack Compose",
            "https://blog.sh=reyaspatil.dev/exploring-pausablecomposition-internals-in-jetpack-compose",
            "Articles",
            "Kotlin DSLs",
        )

        verifyArticle(
            result[8],
            "Contract Test",
            "https://zalas.pl/contract-test/",
            "Articles",
            "Contract Test pattern",
        )

        verifyArticle(
            result[9],
            "Kotlin Clean Code Rearranger",
            "https://plugins.jetbrains.com/plugin/27537-ko=tlin-clean-code-rearranger",
            "Articles",
            "an Intellij IDEA plugin tha rearranges Kotlin",
        )

        verifyArticle(
            result[10],
            "Product for Engineers newsletter",
            "https://newsletter.posthog.com?r=3D5yje7=p",
            "Sponsored",
            "build better products",
        )

        verifyArticle(
            result[11],
            "How to Input Unicode Characters in Maestro Android Tests",
            "https://blog.droidchef.dev/how-to-input-unicode-characters-in-m=aestro-android-tests-a-complete-workaround-guide/",
            "Android",
            "complete workaround",
        )

        verifyArticle(
            result[12],
            "Android MCP SDK with Jason Pearson",
            "https://thebakery.dev/99/",
            "Podcast",
            "Nico Corti speaks with Jason Pearson",
        )

        verifyArticle(
            result[13],
            "Unlock Kotlin Flow: Free Live Webinar",
            "https://webinar.kt=.academy/mastering-kotlin-flow-07",
            "Conferences",
            "Go beyond basics",
        )

        verifyArticle(
            result[14],
            "Liquid Glass",
            "https://github.com/Kyant0/AndroidLiquidGlass",
            "Libraries",
            "Apple's Liquid Glass effect",
        )

        verifyArticle(
            result[15],
            "ScribeSwan",
            "https://gitlab.com/islandoftex/libraries/scribeswan/",
            "Libraries",
            "DSL (domain-sp=ecific language) for creating manpages",
        )
    }

    private fun verifyArticle(
        article: MailContent,
        expectedTitle: String,
        expectedLink: String,
        expectedSection: String,
        expectedContentSnippet: String,
    ) {
        assertNotNull(article, "Article with title containing '$expectedTitle' not found")
        assertEquals(expectedLink, article?.link)
        assertEquals(expectedSection, article?.section)
        assertTrue(
            article?.content?.contains(expectedContentSnippet) ?: false,
            "Article content should contain snippet '$expectedContentSnippet'",
        )
    }
}
