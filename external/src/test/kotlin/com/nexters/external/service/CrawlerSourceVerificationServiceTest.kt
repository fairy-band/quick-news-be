package com.nexters.external.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrawlerSourceVerificationServiceTest {

    private val verificationService = CrawlerSourceVerificationService()

    @Test
    fun `isVerifiedForCrawl should return true for verified domains`() {
        val verifiedUrls = listOf(
            "https://www.maeil-mail.kr/question/17",
            "https://techblog.woowahan.com/26177/",
            "https://kofearticle.substack.com/p/reduxcontext-api-vs",
            "https://dev.to/zrcic/you-built-a-kotlin-ai-agent-3bdi",
            "https://velog.io/@typo/why-senior-engineers-let-bad-projects-fail",
            "https://samsungsds.com/kr/insights/ai.html",
            "https://blog.jetbrains.com/ko/youtrack/guide/",
            "https://aws.amazon.com/blogs/aws/upgrade-eks/",
        )

        for (url in verifiedUrls) {
            assertThat(verificationService.isVerifiedForCrawl(url))
                .`as`("Expected $url to be verified")
                .isTrue()
        }
    }

    @Test
    fun `isVerifiedForCrawl should return false for blocked or unverified domains`() {
        val blockedUrls = listOf(
            "https://links.tldrnewsletter.com/zZrZ2P",
            "https://click.kit-mail6.com/random-hash/aHR0cHM6Ly9...",
            "https://f6p7au8ab.cc.rs6.net/tn.jsp?f=...",
            "https://github.com/yschimke/compose-ai-tools",
            "https://youtu.be/y_QeST7Axrw?si=123",
            "https://twitter.com/user/status/123",
            "https://unknown-random-spam-site.xyz/post/1",
        )

        for (url in blockedUrls) {
            assertThat(verificationService.isVerifiedForCrawl(url))
                .`as`("Expected $url to be blocked or unverified")
                .isFalse()
        }
    }

    @Test
    fun `extractDomain should handle various URL formats correctly`() {
        assertThat(verificationService.extractDomain("https://www.maeil-mail.kr/question/17"))
            .isEqualTo("maeil-mail.kr")
        assertThat(verificationService.extractDomain("http://techblog.woowahan.com/post"))
            .isEqualTo("techblog.woowahan.com")
        assertThat(verificationService.extractDomain("invalid-url"))
            .isNull()
    }
}
