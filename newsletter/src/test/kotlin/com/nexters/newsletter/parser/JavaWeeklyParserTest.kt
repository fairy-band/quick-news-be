package com.nexters.newsletter.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaWeeklyParserTest {
    private val parser = JavaWeeklyParser()

    @Test
    fun `isTarget should return true for Java Weekly emails`() {
        assertTrue(parser.isTarget("Java Weekly <newsletter@libhunt.com>"))
        assertTrue(parser.isTarget("newsletter@libhunt.com"))
        assertTrue(parser.isTarget("Java Weekly"))
    }

    @Test
    fun `isTarget should return false for non-Java Weekly emails`() {
        assertFalse(parser.isTarget("Some Other Newsletter <info@example.com>"))
        assertFalse(parser.isTarget("random@example.com"))
    }

    @Test
    fun `parse should extract articles from Java Weekly newsletter`() {
        // given - 실제 운영 데이터 형식
        val sampleEmail = """
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

        assertEquals(3, articles.size)
        assertEquals(3, projects.size)

        assertEquals("Value Classes Heap Flattening - What to expect from JEP 401 #JVMLS", articles[0].title)
        assertEquals("https://youtu.be/NF4CpL_EWFI", articles[0].link)

        assertEquals("fory", projects[0].title)
        assertEquals("https://www.libhunt.com/r/fory", projects[0].link)
    }
}
