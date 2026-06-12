package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibHuntWeeklyParserTest {
    private val parser = LibHuntWeeklyParser()

    @Test
    fun `supports should return true for shared LibHunt weekly emails`() {
        assertTrue(parser.supports("Java Weekly <newsletter@libhunt.com>", null))
        assertTrue(parser.supports("Awesome iOS Weekly <newsletter@libhunt.com>", null))
        assertTrue(parser.supports("Awesome Kotlin Weekly <newsletter@libhunt.com>", null))
        assertTrue(parser.supports("newsletter@libhunt.com", null))
        assertTrue(parser.supports("Java Weekly", null))
    }

    @Test
    fun `supports should return false for non-LibHunt weekly emails`() {
        assertFalse(parser.supports("Some Other Newsletter <info@example.com>", null))
        assertFalse(parser.supports("random@example.com", null))
    }

    @Test
    fun `parse should extract articles from Java Weekly newsletter`() {
        // given - 실제 운영 데이터 형식
        val sampleEmail =
            """
            This week's Awesome Java Weekly
            Read it on the Web: https://java.libhunt.com/newsletter/494

            ===================
            Awesome Java Weekly
            ===================
            Issue » 494 / Nov 06, 2025

            Your weekly report of the most popular
            Java news, articles and projects
            Popular News and Articles
            -------------------------
            * Value Classes Heap Flattening - What to expect from JEP 401 #JVMLS
              https://youtu.be/NF4CpL_EWFI

            * Agent-O-rama: build LLM agents in Java or Clojure
              https://blog.redplanetlabs.com/2025/11/03/introducing-agent-o-rama

            * Integrity by Default
              https://www.youtube.com/watch?v=uTPRTkny7kQ

            Popular projects
            --------------------------------------
            * fory - https://www.libhunt.com/r/fory

            * opendataloader-pdf - https://www.libhunt.com/r/opendataloader-pdf

            * fernflower - https://www.libhunt.com/r/JetBrains/fernflower

            ---
            Your weekly awesomeness of Awesome Java Weekly news, articles and projects
            """.trimIndent()

        // when
        val result = parser.parse(sampleEmail)

        // then
        assertTrue(result.isNotEmpty())

        val articles = result.filter { it.section == "Popular News and Articles" }
        val projects = result.filter { it.section == "Popular projects" }
        val libraries = result.filter { it.section == "Libraries" }

        assertEquals(3, articles.size)
        assertEquals(0, projects.size)
        assertEquals(1, libraries.size)

        assertEquals("Value Classes Heap Flattening - What to expect from JEP 401 #JVMLS", articles[0].title)
        assertEquals("https://youtu.be/NF4CpL_EWFI", articles[0].link)

        assertEquals("2025년 45주의 라이브러리", libraries[0].title)
        assertEquals("https://java.libhunt.com/newsletter/494", libraries[0].link)
        assertTrue(libraries[0].content.contains("fory"))
        assertTrue(libraries[0].content.contains("opendataloader-pdf"))
        assertTrue(libraries[0].content.contains("fernflower"))
    }

    @Test
    fun `parse should extract articles from iOS LibHunt newsletter`() {
        val sampleEmail =
            """
            This week's Awesome iOS Weekly
            Read it on the Web: https://ios.libhunt.com/newsletter/511

            ==================
            Awesome iOS Weekly
            ==================
            Issue » 511 / Apr 17, 2026

            Your weekly report of the most popular
            iOS news, articles and projects
            Popular News and Articles
            -------------------------
            * Compute iOS XNU offset from kernel cache
              https://blog.reversesociety.co/blog/2026/kernel-rw-not-enough-extract-offsets-from-xnu-kernelcaches

            * Our SwiftUI snapshot tests passed locally but failed on CI. Here's the actual fix.
              https://dev.to/d4g4/our-swiftui-snapshot-tests-passed-locally-but-failed-on-ci-heres-the-actual-fix-5fhd

            Popular projects
            --------------------------------------
            * claude-island - https://www.libhunt.com/r/claude-island

            * PairPods - https://www.libhunt.com/r/PairPods

            ---
            Your weekly awesomeness of Awesome iOS Weekly news, articles and projects
            """.trimIndent()

        val result = parser.parse(sampleEmail)

        val articles = result.filter { it.section == "Popular News and Articles" }
        val libraries = result.filter { it.section == "Libraries" }

        assertEquals(2, articles.size)
        assertEquals("Compute iOS XNU offset from kernel cache", articles[0].title)
        assertEquals(
            "https://blog.reversesociety.co/blog/2026/kernel-rw-not-enough-extract-offsets-from-xnu-kernelcaches",
            articles[0].link,
        )

        assertEquals(1, libraries.size)
        assertEquals("https://ios.libhunt.com/newsletter/511", libraries[0].link)
        assertTrue(libraries[0].content.contains("claude-island"))
        assertTrue(libraries[0].content.contains("PairPods"))
    }
}
