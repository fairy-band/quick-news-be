package com.nexters.newsletter.service

import com.nexters.external.entity.NewsletterSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NewsletterProviderNameResolverTest {
    @Test
    fun `resolve should map iOS sender aliases to provider names`() {
        assertThat(resolve(sender = "Dave Verwer", senderEmail = "dave@iosdevweekly.com"))
            .isEqualTo("iOS Dev Weekly")
        assertThat(resolve(sender = "tiagohenriques@ioscoffeebreak.com", senderEmail = "tiagohenriques@ioscoffeebreak.com"))
            .isEqualTo("iOS Coffee Break")
        assertThat(resolve(sender = "Majid Jabrayilov", senderEmail = "swiftuiweekly@substack.com"))
            .isEqualTo("SwiftUI Weekly")
        assertThat(resolve(sender = "Vincent Pradeilles", senderEmail = "vincent@swiftwithvincent.com"))
            .isEqualTo("Swift with Vincent")
    }

    @Test
    fun `resolve should prefer specific LibHunt sender over shared sender email`() {
        assertThat(resolve(sender = "Awesome iOS Weekly", senderEmail = "newsletter@libhunt.com"))
            .isEqualTo("Awesome iOS Weekly")
        assertThat(resolve(sender = "Awesome Android Newsletter", senderEmail = "newsletter@libhunt.com"))
            .isEqualTo("Awesome Android Newsletter")
        assertThat(resolve(sender = "Awesome Kotlin Weekly", senderEmail = "newsletter@libhunt.com"))
            .isEqualTo("Awesome Kotlin Weekly")
        assertThat(resolve(sender = "Awesome Java Weekly", senderEmail = "newsletter@libhunt.com"))
            .isEqualTo("Awesome Java Newsletter")
    }

    @Test
    fun `resolve should map shared LibHunt sender from rss headers`() {
        assertThat(
            NewsletterProviderNameResolver.resolve(
                source(
                    sender = "newsletter@libhunt.com",
                    senderEmail = "newsletter@libhunt.com",
                    headers = mapOf("RSS-Item-URL" to "https://ios.libhunt.com/newsletter/511"),
                ),
            ),
        ).isEqualTo("Awesome iOS Weekly")

        assertThat(
            NewsletterProviderNameResolver.resolve(
                source(
                    sender = "newsletter@libhunt.com",
                    senderEmail = "newsletter@libhunt.com",
                    headers = mapOf("RSS-Item-URL" to "https://java.libhunt.com/newsletter/511"),
                ),
            ),
        ).isEqualTo("Awesome Java Newsletter")
    }

    @Test
    fun `resolve should map provider from rss headers`() {
        val source =
            source(
                sender = "Swift Articles",
                senderEmail = "rss@www.avanderlee.com",
                headers = mapOf("RSS-Feed-URL" to "https://www.avanderlee.com/feed/"),
            )

        assertThat(NewsletterProviderNameResolver.resolve(source)).isEqualTo("SwiftLee Weekly")
    }

    @Test
    fun `resolve should prefer Jacob sender over Fatbobman subject mention`() {
        val source =
            source(
                sender = "Jacob's Tech Tavern",
                senderEmail = "jacobbartlett@substack.com",
                subject = "A Fatbobman article worth reading",
            )

        assertThat(NewsletterProviderNameResolver.resolve(source)).isEqualTo("Jacob's Tech Tavern")
    }

    @Test
    fun `resolve should fallback to sender`() {
        assertThat(resolve(sender = "Unknown Newsletter", senderEmail = "unknown@example.com"))
            .isEqualTo("Unknown Newsletter")
    }

    private fun resolve(
        sender: String,
        senderEmail: String,
    ): String = NewsletterProviderNameResolver.resolve(source(sender = sender, senderEmail = senderEmail))

    private fun source(
        sender: String,
        senderEmail: String,
        subject: String = "Subject",
        headers: Map<String, String> = emptyMap(),
    ): NewsletterSource =
        NewsletterSource(
            subject = subject,
            sender = sender,
            senderEmail = senderEmail,
            recipient = "system",
            recipientEmail = "system@newsletter.ai",
            content = "content",
            contentType = "text/html",
            receivedDate = LocalDateTime.of(2026, 6, 11, 0, 0),
            headers = headers,
        )
}
