package com.nexters.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.nexters.api.dto.CreateContentApiRequest
import com.nexters.api.util.TokenUtil
import com.nexters.external.entity.AdminMember
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.repository.AdminMemberRepository
import com.nexters.external.service.ContentService
import com.nexters.external.service.ExposureContentService
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

@WebMvcTest(NewsletterApiController::class)
@ActiveProfiles("test")
class NewsletterApiControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var dayArchiveResolver: DailyContentArchiveResolver

    @MockitoBean
    private lateinit var exposureContentService: ExposureContentService

    @MockitoBean
    private lateinit var contentService: ContentService

    @MockitoBean
    private lateinit var tokenUtil: TokenUtil

    @MockitoBean
    private lateinit var adminMemberRepository: AdminMemberRepository

    private lateinit var validToken: String
    private lateinit var adminMember: AdminMember

    @BeforeEach
    fun setUp() {
        adminMember =
            AdminMember(
                id = 1L,
                email = "admin@example.com",
                name = "Admin User"
            )
        validToken = Base64.getEncoder().encodeToString("admin@example.com:token".toByteArray())
    }

    @Test
    fun `should create content successfully with valid token`() {
        // Given
        val request =
            CreateContentApiRequest(
                title = "Kotlin 최신 기능 소개",
                content = "Kotlin 1.9에서 새로운 기능들이 추가되었습니다...",
                contentProviderName = "Kotlin Weekly",
                originalUrl = "https://example.com/article",
                publishedAt = LocalDate.now(),
            )

        val createdContent =
            Content(
                id = 1L,
                title = request.title,
                content = request.content,
                newsletterName = request.contentProviderName,
                originalUrl = request.originalUrl,
                publishedAt = request.publishedAt,
                contentProvider =
                    ContentProvider(
                        id = 1L,
                        name = "Kotlin Weekly",
                        channel = "Kotlin Weekly",
                        language = "ko",
                        type = null
                    ),
                createdAt = LocalDateTime.now()
            )

        // When & Then
        org.mockito.Mockito
            .`when`(tokenUtil.validateAndGetEmail(validToken))
            .thenReturn("admin@example.com")
        org.mockito.Mockito
            .`when`(
                contentService.createContent(
                    title = request.title,
                    content = request.content,
                    contentProviderName = request.contentProviderName,
                    originalUrl = request.originalUrl,
                    publishedAt = request.publishedAt,
                    newsletterSourceId = null,
                )
            ).thenReturn(createdContent)

        mockMvc
            .perform(
                post("/api/newsletters/contents")
                    .header("Access-Token", validToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.title").value("Kotlin 최신 기능 소개"))
            .andExpect(jsonPath("$.newsletterName").value("Kotlin Weekly"))
            .andExpect(jsonPath("$.originalUrl").value("https://example.com/article"))
    }

    @Test
    fun `should return 400 when access-token header is missing`() {
        // Given
        val request =
            CreateContentApiRequest(
                title = "Kotlin 최신 기능 소개",
                content = "Kotlin 1.9에서 새로운 기능들이 추가되었습니다...",
                contentProviderName = "Kotlin Weekly",
                originalUrl = "https://example.com/article",
                publishedAt = LocalDate.now(),
            )

        // When & Then
        mockMvc
            .perform(
                post("/api/newsletters/contents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 401 when token is invalid`() {
        // Given
        val request =
            CreateContentApiRequest(
                title = "Kotlin 최신 기능 소개",
                content = "Kotlin 1.9에서 새로운 기능들이 추가되었습니다...",
                contentProviderName = "Kotlin Weekly",
                originalUrl = "https://example.com/article",
                publishedAt = LocalDate.now(),
            )

        val invalidToken = "invalid_token"

        // When & Then
        org.mockito.Mockito
            .`when`(tokenUtil.validateAndGetEmail(invalidToken))
            .thenThrow(IllegalArgumentException("Invalid token"))

        mockMvc
            .perform(
                post("/api/newsletters/contents")
                    .header("Access-Token", invalidToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should create content with newsletter source id`() {
        // Given
        val request =
            CreateContentApiRequest(
                title = "Test Article",
                content = "Test content",
                contentProviderName = "Test Newsletter",
                originalUrl = "https://example.com/test",
                publishedAt = LocalDate.now(),
            )

        val createdContent =
            Content(
                id = 2L,
                title = request.title,
                content = request.content,
                newsletterName = request.contentProviderName,
                originalUrl = request.originalUrl,
                publishedAt = request.publishedAt,
                contentProvider =
                    ContentProvider(
                        id = 2L,
                        name = "Test Provider",
                        channel = "Test Provider",
                        language = "ko",
                        type = null
                    ),
                createdAt = LocalDateTime.now()
            )

        // When & Then
        org.mockito.Mockito
            .`when`(tokenUtil.validateAndGetEmail(validToken))
            .thenReturn("admin@example.com")
        org.mockito.Mockito
            .`when`(
                contentService.createContent(
                    title = request.title,
                    content = request.content,
                    contentProviderName = request.contentProviderName,
                    originalUrl = request.originalUrl,
                    publishedAt = request.publishedAt,
                )
            ).thenReturn(createdContent)

        mockMvc
            .perform(
                post("/api/newsletters/contents")
                    .header("Access-Token", validToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(2L))
            .andExpect(jsonPath("$.title").value("Test Article"))
    }
}
