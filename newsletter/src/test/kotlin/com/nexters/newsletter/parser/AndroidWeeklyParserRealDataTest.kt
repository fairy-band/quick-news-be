package com.nexters.newsletter.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidWeeklyParserRealDataTest {
    private val parser = AndroidWeeklyParser()

    @Test
    fun `should parse real Android Weekly #685 content`() {
        val content =
            """
            Plain Text: View in web browser 685 July 27th, 2025 Articles & Tutorials Sponsored 🚨 Firebase Dynamic Links is shutting down Deep links power core user journeys, but Firebase Dynamic Links will shut down this August. Switch to the Airbridge DeepLink Plan, a powerful SDK-based solution with full support for Android and iOS. It's free for up to 10K MAU. Whether you're migrating existing links or starting from scratch, now is the time to build smarter and with less stress. Easy to integrate. Fully documented. Ending Android USB Freezes Tom Mulcahy describes how blocking in AOSP's USB stack caused Android app freezes, and how Block's upstream Android 16 patches resolved it, boosting performance by ~40%. How to Encrypt Your Room Database in Android Using SQLCipher Pouya Heydari shows how to secure Room by generating an SQLCipher passphrase, storing it with EncryptedSharedPreferences, and configuring Room with a SupportFactory to enable transparent encryption. Play Billing Library 8 support in Purchases SDK v9.0.0 Jaewoong Eum highlights Billing Library 8's key changes—more flexibility for one‑time purchases, non‑expiring subs, improved error responses and removed deprecated queries—with seamless support through RevenueCat SDK v9.0.0 StyledString: A Better Pattern for Rich Text in Jetpack Compose Eury Pérez Beltré introduces StyledString, a cleaner abstraction over AnnotatedString allowing rich, interactive text styling without index math or boilerplate. Dotify | Rendering Retro Style Text in Compose with Bitmaps Nikhil Mandlik demonstrates creating retro-style text in Compose by rendering text into a bitmap, downscaling to a dot‑matrix, and drawing visible pixels as colored dots. Fragment Navigation to Navigation 3 Compose Iago Fucolo demonstrates migrating a fragment-based navigation flow to Navigation 3 using Compose screens, NavDisplay, mutable back stack, and entry decorators for state and transitions. The Complete Guide to Flow Cancellation Techniques Sahil Thakar explains how to cancel Kotlin Flows reliably using cooperative cancellation, Flow operators, and lifecycle-aware scopes to avoid leaks and improve performance. Smooth Animated Bottom Sheet Header with Jetpack Compose Asha Mishra demonstrates animating a bottom sheet header's elevation, scale, and corner radius in Jetpack Compose according to swipe progress. The dark corners of inline, crossinline, and reified in Kotlin Dmitry Glazunov highlights misuse of Kotlin's inline, crossinline, and reified modifiers that can damage debuggability, cause unintended API leaks, or lead to performance surprises. Search Dialog Component — A compose implementation Davies Adedayo AbdulGafar presents a fast, minimal custom Compose search dialog using suffix array, debounced input, and highlighted matches. Understanding SideEffects in Jetpack Compose : Logging and Beyond in Compose Richa Sharma explains that Compose's SideEffect API lets you safely run non‑suspending operations after every successful recomposition to maintain predictable UI‑state interactions. Place a sponsored post Advertise to more than 80k Android developers! We reach out to more than 80k Android developers around the world, every week, through our email newsletter and social media channels. Advertise your Android development related service or product! Jobs Senior Android Engineer (Remote) Join DuckDuckGo, a remote-first company dedicated to raising online trust standards, where a culture of trust, inclusivity, and empowered project management allows team members to take full ownership of their projects. We're looking for an experienced Android engineer with 7+ years in native apps, s Libraries & Code TimelineView A synchronized dual-view timeline visualization component for Android with native Compose support. Deepr Deepr is a native Android application designed to streamline the management and testing of deeplinks. It provides a simple and efficient way to store, organize, and launch deeplinks, making it an essential tool for developers and testers. Videos & Podcasts 2 Ways To Display DateTime In Compose UI Jov Mit demonstrates 2 ways to display DateTime in Compose UI. How to Implement Pagination In Compose Multiplatform (KMP) Philipp Lackner shares a generic approach to paginate data from a remote API or local database. Submit your library to the Kotlin Foundation Grants Program until July 31! The Kotlin Foundation provides funding and promotion for standout libraries. If you maintain a library in a field such as Kotlin Multiplatform or AI tooling consider submitting it at https://kotl.in/grants! Prepare your Play app for devices with 16 KB page sizes Steven Moreland on the Android Systems Team shares the latest news for Android developers. Enrich your app with live updates and widgets - YouTube Learn how to use Android live updates and widgets to create rich user experiences! In this video, see notifications (progress style template & live updates) and widgets (canonical layouts & generated previews). Kobweb, Kotlin & Cross-Platform chat with David Herman David Herman discusses Kobweb, a Kotlin web framework build on top of Compose HTML. Junie Livestream: Practical Spring Boot Development with AI Tools Anton Arhipov livestreams coding with Jetbrains Junie. POST A JOB SPONSORED POST PATREON MERCHANDISE TWITTER AndroidWeekly.net | Kortumstr. 19-21, Bochum, 44787 Germany Unsubscribe newsletter.feeding@gmail.com Update Profile | Our Privacy Policy | Constant Contact Data Notice Sent by contact@androidweekly.net powered by Try email marketing for free today!
            """.trimIndent()

        val results = parser.parse(content)

        // Verify parsing results
        assertTrue(results.isNotEmpty(), "Should parse at least one article")

        // Print results for debugging
        println("Parsed ${results.size} items:")
        results.forEach { item ->
            println("- ${item.title} (${item.section})")
        }

        // Check specific articles we know should be parsed
        val expectedTitles =
            listOf(
                "Ending Android USB Freezes",
                "How to Encrypt Your Room Database in Android Using SQLCipher",
                "StyledString: A Better Pattern for Rich Text in Jetpack Compose",
                "TimelineView",
                "Deepr"
            )

        expectedTitles.forEach { expectedTitle ->
            assertTrue(
                results.any { it.title.contains(expectedTitle, ignoreCase = true) },
                "Should find article: $expectedTitle"
            )
        }

        // Verify sections are correct
        val articlesSectionCount = results.count { it.section == "Articles & Tutorials" }
        val librariesSectionCount = results.count { it.section == "Libraries & Code" }
        val videosSectionCount = results.count { it.section == "Videos & Podcasts" }

        assertTrue(articlesSectionCount > 0, "Should have Articles & Tutorials section items")
        assertTrue(librariesSectionCount > 0, "Should have Libraries & Code section items")
        assertTrue(videosSectionCount > 0, "Should have Videos & Podcasts section items")

        // Verify no sponsored content is included
        assertFalse(
            results.any { it.section == "Sponsored" },
            "Should not include sponsored content"
        )
    }
}
