package com.nexters.external.apiclient

import com.nexters.external.entity.WebPageCrawlCache
import com.nexters.external.repository.WebPageCrawlCacheRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CrawlerServiceClientTest {

    private val cacheRepository = mockk<WebPageCrawlCacheRepository>(relaxed = true)

    @Test
    fun `extractArticle should return cached response directly on cache hit`() {
        val targetUrl = "https://www.maeil-mail.kr/question/17"
        val cachedEntity = WebPageCrawlCache(
            url = targetUrl,
            title = "매일메일 질문 17번",
            content = "이것은 캐시된 본문입니다.",
            imageUrl = "https://dp71rnme1p14w.cloudfront.net/og.png",
            length = 15,
            success = true,
        )

        every { cacheRepository.findByUrl(targetUrl) } returns cachedEntity

        val client = CrawlerServiceClient(
            crawlerServiceUrl = "http://localhost:9999", // Unreachable port to prove no HTTP call
            webPageCrawlCacheRepository = cacheRepository,
        )

        val result = client.extractArticle(targetUrl)

        assertThat(result).isNotNull
        assertThat(result!!.success).isTrue()
        assertThat(result.title).isEqualTo("매일메일 질문 17번")
        assertThat(result.content).isEqualTo("이것은 캐시된 본문입니다.")
        assertThat(result.imageUrl).isEqualTo("https://dp71rnme1p14w.cloudfront.net/og.png")
        assertThat(result.length).isEqualTo(15)

        verify(exactly = 1) { cacheRepository.findByUrl(targetUrl) }
        verify(exactly = 0) { cacheRepository.save(any()) }
    }

    @Test
    fun `extractArticle should skip crawl for unverified source`() {
        val verificationService = com.nexters.external.service.CrawlerSourceVerificationService()
        val unverifiedUrl = "https://links.tldrnewsletter.com/zZrZ2P"

        val client = CrawlerServiceClient(
            crawlerServiceUrl = "http://localhost:9999",
            webPageCrawlCacheRepository = cacheRepository,
            crawlerSourceVerificationService = verificationService,
        )

        val result = client.extractArticle(unverifiedUrl)

        assertThat(result).isNull()
        verify(exactly = 0) { cacheRepository.findByUrl(unverifiedUrl) }
    }
}
