package com.nexters.external.service

import com.nexters.external.dto.RssFeedConfig
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class RssReaderServiceTest {
    private val representativeImageUrlExtractorService = mockk<RepresentativeImageUrlExtractorService>()
    private val servers = mutableListOf<HttpServer>()

    @AfterEach
    fun tearDown() {
        servers.forEach { server -> server.stop(0) }
    }

    @Test
    fun `fetchFeed should normalize relative item links against feed link`() {
        every { representativeImageUrlExtractorService.extractFromHtml(any(), any()) } returns null
        val server =
            startServer(
                status = 200,
                body =
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <rss version="2.0">
                        <channel>
                            <title>Test Feed</title>
                            <link>https://example.com/blog/index.html</link>
                            <description>test</description>
                            <item>
                                <title>Relative Link Item</title>
                                <link>/posts/relative-item</link>
                                <description>hello</description>
                            </item>
                        </channel>
                    </rss>
                    """.trimIndent(),
            )

        val feed = rssReaderService().fetchFeed(server.feedUrl())

        assertThat(feed).isNotNull
        assertThat(feed!!.items).hasSize(1)
        assertThat(feed.items.first().link).isEqualTo("https://example.com/posts/relative-item")
    }

    @Test
    fun `fetchFeed should not retry non retryable http errors`() {
        every { representativeImageUrlExtractorService.extractFromHtml(any(), any()) } returns null
        val requestCount = AtomicInteger()
        val server =
            startServer(
                status = 403,
                body = "forbidden",
                onRequest = { requestCount.incrementAndGet() },
            )

        val feed =
            rssReaderService(
                config = RssFeedConfig(maxRetries = 3, retryDelayMs = 1),
            ).fetchFeed(server.feedUrl())

        assertThat(feed).isNull()
        assertThat(requestCount.get()).isEqualTo(1)
    }

    @Test
    fun `fetchFeed should remove invalid xml control characters before parsing`() {
        every { representativeImageUrlExtractorService.extractFromHtml(any(), any()) } returns null
        val invalidControlCharacter = "\u001C"
        val server =
            startServer(
                status = 200,
                body =
                    """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <rss version="2.0">
                        <channel>
                            <title>Test Feed</title>
                            <link>https://example.com</link>
                            <description>test</description>
                            <item>
                                <title>Invalid Character Item</title>
                                <link>https://example.com/posts/invalid-character</link>
                                <description><![CDATA[hello ${invalidControlCharacter}world]]></description>
                            </item>
                        </channel>
                    </rss>
                    """.trimIndent(),
            )

        val feed = rssReaderService().fetchFeed(server.feedUrl())

        assertThat(feed).isNotNull
        assertThat(feed!!.items).hasSize(1)
        assertThat(feed.items.first().description).isEqualTo("hello world")
    }

    private fun rssReaderService(config: RssFeedConfig = RssFeedConfig(maxRetries = 1, retryDelayMs = 1)): RssReaderService =
        RssReaderService(
            representativeImageUrlExtractorService = representativeImageUrlExtractorService,
            config = config,
        )

    private fun startServer(
        status: Int,
        body: String,
        onRequest: () -> Unit = {},
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/feed.xml") { exchange ->
            onRequest()
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/rss+xml; charset=UTF-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { output -> output.write(bytes) }
        }
        server.start()
        servers += server
        return server
    }

    private fun HttpServer.feedUrl(): String = "http://localhost:${address.port}/feed.xml"
}
