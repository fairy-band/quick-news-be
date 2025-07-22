package com.nexters.newsletterfeeder.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AndroidWeeklyParserTest {
    private val sut = AndroidWeeklyParser()

    @Test
    fun `isTarget should return true for Android Weekly emails`() {
        assertTrue(sut.isTarget("Android Weekly <contact@androidweekly.net>"))
        assertTrue(sut.isTarget("newsletter@androidweekly.net"))
        assertTrue(sut.isTarget("Android Weekly"))
    }

    @Test
    fun `isTarget should return false for non-Android Weekly emails`() {
        assertFalse(sut.isTarget("Some Other Newsletter <info@example.com>"))
        assertFalse(sut.isTarget("random@example.com"))
    }

    @Test
    fun `parse should extract articles from Android Weekly newsletter`() {
        // given
        val sampleEmail = javaClass.classLoader.getResource("android-weekly-sample.html")?.readText()
            ?: error("sample html not found")

        // when
        val result = sut.parse(sampleEmail)
        println("Parsed size=" + result.size)
        result.forEach { println(it.title) }

        // then
        val expectedTitles = listOf(
            "Fast, flake-free, and fully parallel automated testing for Apps",
            "Exploring PausableComposition internals in Jetpack Compose",
            "Live Templates in Android Studio",
            "Advertise to more than 80k Android developers!",
            "Compose stability tips and tricks",
            "Add Speed Effects to Your Android Videos Using Media3",
            "A Journey with KSP and KotlinPoet",
            "Building Your First Kotlin Multiplatform App",
            "Breaking to Build: Fuzzing the Kotlin Compiler",
            "How to Answer Hashing Like a Java/Kotlin Expert",
            "play-store-mcp",
            "Kombinator",
            "compose-floating-tab-bar",
            "New tools to help drive success for one-time products",
            "Agent mode in Android Studio",
            "Free webinar — Mastering Kotlin Flow",
        )

        expectedTitles.forEach { title ->
            assertTrue(result.any { it.title.contains(title, ignoreCase = true) }, "Expected title '$title' not found")
        }
        assertEquals(16, result.size, "There should be exactly 16 matched titles")
    }
}
