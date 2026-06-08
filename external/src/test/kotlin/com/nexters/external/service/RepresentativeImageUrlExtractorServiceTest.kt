package com.nexters.external.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RepresentativeImageUrlExtractorServiceTest {
    private val extractor = RepresentativeImageUrlExtractorService()

    @Test
    fun `extractFromHtml should prefer Open Graph image`() {
        val html =
            """
            <html><head>
              <meta property="og:image" content="/images/og.png" />
              <meta name="twitter:image" content="https://example.com/images/twitter.png" />
              <link rel="image_src" href="https://example.com/images/link.png" />
            </head></html>
            """.trimIndent()

        val result = extractor.extractFromHtml(html, "https://example.com/posts/1")

        assertThat(result).isEqualTo("https://example.com/images/og.png")
    }

    @Test
    fun `extractFromHtml should fallback to JSON-LD image`() {
        val html =
            """
            <html><head>
              <script type="application/ld+json">
                {
                  "@type": "NewsArticle",
                  "image": {
                    "url": "/images/json-ld.png"
                  }
                }
              </script>
            </head></html>
            """.trimIndent()

        val result = extractor.extractFromHtml(html, "https://example.com/posts/1")

        assertThat(result).isEqualTo("https://example.com/images/json-ld.png")
    }

    @Test
    fun `extractFromHtml should fallback to first non decorative image`() {
        val html =
            """
            <html><body>
              <img src="/pixel.gif" width="1" height="1" />
              <article><img src="images/article.png" width="640" height="360" /></article>
            </body></html>
            """.trimIndent()

        val result = extractor.extractFromHtml(html, "https://example.com/posts/1")

        assertThat(result).isEqualTo("https://example.com/posts/images/article.png")
    }
}
