package com.nexters.external.apiclient

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
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
    val length: Int = 0,
    val error: String? = null,
)

@Component
open class CrawlerServiceClient(
    @Value("\${crawler.service.url:http://crawler:8000}")
    private val crawlerServiceUrl: String = "http://crawler:8000",
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
     * @param url 추출 대상 아티클 원문 URL
     * @return 추출 결과 (성공 여부, 본문 텍스트 등)
     */
    open fun extractArticle(url: String): ArticleExtractResponse? {
        return try {
            restClient
                .post()
                .uri { builder ->
                    builder
                        .path("/api/v1/extract")
                        .queryParam("url", url)
                        .build()
                }
                .retrieve()
                .body(ArticleExtractResponse::class.java)
        } catch (e: Exception) {
            logger.warn("CrawlerServiceClient failed to extract article for url=$url: ${e.message}")
            null
        }
    }
}
