package com.nexters.external.apiclient

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.nexters.external.entity.WebPageCrawlCache
import com.nexters.external.repository.WebPageCrawlCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArticleExtractResponse(
    val url: String = "",
    val success: Boolean = false,
    val title: String? = null,
    val content: String? = null,
    @JsonAlias("image_url", "imageUrl")
    @JsonProperty("image_url")
    val imageUrl: String? = null,
    val length: Int = 0,
    val error: String? = null,
)

@Component
open class CrawlerServiceClient(
    @Value("\${crawler.service.url:http://crawler:8000}")
    private val crawlerServiceUrl: String = "http://crawler:8000",
    @Autowired(required = false)
    private val webPageCrawlCacheRepository: WebPageCrawlCacheRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(CrawlerServiceClient::class.java)

    private val restClient: RestClient =
        RestClient.builder()
            .baseUrl(crawlerServiceUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(45000)
                }
            )
            .build()

    /**
     * URL로부터 아티클 전문 본문을 추출합니다.
     * MongoDB 캐시에 유효한 결과가 존재할 경우 외부 네트워크 요청 없이 즉시 반환하며,
     * 캐시 미스 시 Python 크롤러 서비스를 호출하여 7일 TTL 캐시에 저장 후 반환합니다.
     *
     * @param url 추출 대상 아티클 원문 URL
     * @return 추출 결과 (성공 여부, 본문 텍스트, 대표 이미지 URL 등)
     */
    open fun extractArticle(url: String): ArticleExtractResponse? {
        val normalizedUrl = url.trim()

        // 1. Cache hit check
        try {
            val cached = webPageCrawlCacheRepository?.findByUrl(normalizedUrl)
            if (cached != null && cached.success && !cached.content.isNullOrBlank()) {
                logger.info("Crawler cache hit for url: {}", normalizedUrl)
                return ArticleExtractResponse(
                    url = cached.url,
                    success = cached.success,
                    title = cached.title,
                    content = cached.content,
                    imageUrl = cached.imageUrl,
                    length = cached.length,
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to check crawler cache for url={}: {}", normalizedUrl, e.message)
        }

        // 2. Fetch from microservice
        return try {
            val response = restClient
                .post()
                .uri { builder ->
                    builder
                        .path("/api/v1/extract")
                        .queryParam("url", normalizedUrl)
                        .build()
                }
                .retrieve()
                .body(ArticleExtractResponse::class.java)

            // 3. Save to MongoDB cache on success
            if (response != null && response.success && !response.content.isNullOrBlank()) {
                saveToCache(response)
            }

            response
        } catch (e: Exception) {
            logger.warn("CrawlerServiceClient failed to extract article for url=$normalizedUrl: ${e.message}")
            null
        }
    }

    private fun saveToCache(response: ArticleExtractResponse) {
        try {
            webPageCrawlCacheRepository?.save(
                WebPageCrawlCache(
                    url = response.url,
                    title = response.title,
                    content = response.content,
                    imageUrl = response.imageUrl,
                    length = response.length,
                    success = response.success,
                )
            )
            logger.info("Saved crawler result to MongoDB cache (7d TTL): {}", response.url)
        } catch (e: Exception) {
            logger.warn("Failed to save crawler result to cache for {}: {}", response.url, e.message)
        }
    }
}
