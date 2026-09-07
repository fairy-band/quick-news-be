package com.nexters.external.apiclient

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class EmbeddingServiceClient(
    @Value("\${embedding.service.url:http://newsletter-embedder:8000}")
    private val embeddingServiceUrl: String,
) {
    private val logger = LoggerFactory.getLogger(EmbeddingServiceClient::class.java)

    private val restClient: RestClient =
        RestClient.builder()
            .baseUrl(embeddingServiceUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(15000)
                }
            )
            .build()

    /**
     * 특정 콘텐츠의 bge-m3 임베딩 생성을 embedding-service에 비동기/동기 요청합니다.
     * @param contentId 임베딩을 생성할 contents.id
     * @return 임베딩 생성 및 pgvector 적재 성공 여부
     */
    fun embedContent(contentId: Long): Boolean {
        return try {
            val response =
                restClient
                    .post()
                    .uri("/embed/content/{contentId}", contentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()

            if (response.statusCode.is2xxSuccessful) {
                logger.info("Successfully requested embedding for content ID: $contentId")
                true
            } else {
                logger.warn("Embedding service returned non-2xx for content ID $contentId: ${response.statusCode}")
                false
            }
        } catch (e: Exception) {
            logger.warn("Failed to generate embedding for content ID $contentId: ${e.message}")
            false
        }
    }

    /**
     * 미처리된 전체 콘텐츠에 대한 일괄 임베딩 파이프라인 트리거
     */
    fun triggerBatchPipeline(overwrite: Boolean = false): Boolean {
        return try {
            val response =
                restClient
                    .post()
                    .uri("/pipeline/embed-all?overwrite={overwrite}", overwrite)
                    .retrieve()
                    .toBodilessEntity()
            response.statusCode.is2xxSuccessful
        } catch (e: Exception) {
            logger.warn("Failed to trigger batch embedding pipeline: ${e.message}")
            false
        }
    }
}
