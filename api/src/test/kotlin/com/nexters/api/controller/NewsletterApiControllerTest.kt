package com.nexters.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.nexters.api.dto.CreateContentApiRequest
import com.nexters.api.service.NewsletterContentsService
import com.nexters.api.util.TokenUtil
import com.nexters.external.entity.AdminMember
import com.nexters.external.entity.Content
import com.nexters.external.entity.ContentProvider
import com.nexters.external.entity.DailyContentArchive
import com.nexters.external.entity.ExposureContent
import com.nexters.external.repository.AdminMemberRepository
import com.nexters.external.service.ContentProviderRequestService
import com.nexters.external.service.ContentService
import com.nexters.external.service.ExposureContentService
import com.nexters.external.service.PopularNewsletterSnapshotService
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

@WebMvcTest(NewsletterApiController::class)
@Import(NewsletterContentsService::class)
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
    private lateinit var popularNewsletterSnapshotService: PopularNewsletterSnapshotService

    @MockitoBean
    private lateinit var contentService: ContentService

    @MockitoBean
    private lateinit var tokenUtil: TokenUtil

    @MockitoBean
    private lateinit var adminMemberRepository: AdminMemberRepository

    @MockitoBean
    private lateinit var contentProviderRequestService: ContentProviderRequestService

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
    fun `should return featured trending card from snapshot in v1 response`() {
        val publishedDate = LocalDate.of(2025, 7, 8)
        val archive =
            DailyContentArchive(
                date = publishedDate,
                user =
                    DailyContentArchive.UserSnapshot(
                        id = 1L,
                        deviceToken = "device-token",
                        createdAt = LocalDateTime.of(2025, 7, 8, 0, 0),
                        updatedAt = LocalDateTime.of(2025, 7, 8, 0, 0),
                    ),
                exposureContents =
                    listOf(
                        ExposureContent(
                            id = 11L,
                            content =
                                Content(
                                    id = 101L,
                                    title = "원문 제목",
                                    content = "원문 본문",
                                    newsletterName = "안드로이드 위클리",
                                    originalUrl = "https://example.com/article-1",
                                    imageUrl = "https://example.com/image-1.png",
                                    publishedAt = publishedDate,
                                    contentProvider =
                                        ContentProvider(
                                            id = 201L,
                                            name = "Android Weekly",
                                            channel = "android-weekly",
                                            language = "ko",
                                            type = null,
                                        ),
                                ),
                            provocativeKeyword = "Kotlin",
                            provocativeHeadline = "후킹 제목",
                            summaryContent = "요약 내용",
                        ),
                    ),
            )
        val featuredExposureContent =
            ExposureContent(
                id = 99L,
                content =
                    Content(
                        id = 199L,
                        title = "인기 원문 제목",
                        content = "인기 원문 본문",
                        newsletterName = "프론트엔드 위클리",
                        originalUrl = "https://example.com/featured-article",
                        imageUrl = "https://example.com/featured-image.png",
                        publishedAt = publishedDate.minusDays(1),
                        contentProvider =
                            ContentProvider(
                                id = 299L,
                                name = "Frontend Weekly",
                                channel = "frontend-weekly",
                                language = "en",
                                type = null,
                            ),
                    ),
                provocativeKeyword = "React",
                provocativeHeadline = "가장 많이 읽힌 글",
                summaryContent = "인기 글 요약",
            )

        Mockito
            .`when`(dayArchiveResolver.resolveTodayContentArchive(1L, publishedDate))
            .thenReturn(archive)
        Mockito
            .`when`(popularNewsletterSnapshotService.findLatestFeaturedExposureContent())
            .thenReturn(featuredExposureContent)

        mockMvc
            .perform(
                get("/api/newsletters/contents/1")
                    .param("publishedDate", publishedDate.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.publishedDate").value("2025-07-08"))
            .andExpect(jsonPath("$.trendingCard.id").value(99L))
            .andExpect(jsonPath("$.trendingCard.title").value("가장 많이 읽힌 글"))
            .andExpect(jsonPath("$.trendingCard.topKeyword").value("React"))
            .andExpect(jsonPath("$.trendingCard.summary").value("인기 글 요약"))
            .andExpect(jsonPath("$.trendingCard.contentUrl").value("https://example.com/featured-article"))
            .andExpect(jsonPath("$.trendingCard.imageUrl").value("https://example.com/featured-image.png"))
            .andExpect(jsonPath("$.trendingCard.newsletterName").value("프론트엔드 위클리"))
            .andExpect(jsonPath("$.trendingCard.language").value("ENGLISH"))
            .andExpect(jsonPath("$.cards[0].id").value(11L))
            .andExpect(jsonPath("$.cards[0].title").value("후킹 제목"))
            .andExpect(jsonPath("$.cards[0].topKeyword").value("Kotlin"))
            .andExpect(jsonPath("$.cards[0].summary").value("요약 내용"))
            .andExpect(jsonPath("$.cards[0].contentUrl").value("https://example.com/article-1"))
            .andExpect(jsonPath("$.cards[0].imageUrl").value("https://example.com/image-1.png"))
            .andExpect(jsonPath("$.cards[0].newsletterName").value("안드로이드 위클리"))
            .andExpect(jsonPath("$.cards[0].language").value("KOREAN"))
    }

    @Test
    fun `should fallback featured trending card to first card in v1 response`() {
        val publishedDate = LocalDate.of(2025, 7, 8)
        val archive =
            DailyContentArchive(
                date = publishedDate,
                user =
                    DailyContentArchive.UserSnapshot(
                        id = 1L,
                        deviceToken = "device-token",
                        createdAt = LocalDateTime.of(2025, 7, 8, 0, 0),
                        updatedAt = LocalDateTime.of(2025, 7, 8, 0, 0),
                    ),
                exposureContents =
                    listOf(
                        ExposureContent(
                            id = 11L,
                            content =
                                Content(
                                    id = 101L,
                                    title = "원문 제목",
                                    content = "원문 본문",
                                    newsletterName = "안드로이드 위클리",
                                    originalUrl = "https://example.com/article-1",
                                    imageUrl = "https://example.com/image-1.png",
                                    publishedAt = publishedDate,
                                    contentProvider =
                                        ContentProvider(
                                            id = 201L,
                                            name = "Android Weekly",
                                            channel = "android-weekly",
                                            language = "ko",
                                            type = null,
                                        ),
                                ),
                            provocativeKeyword = "Kotlin",
                            provocativeHeadline = "후킹 제목",
                            summaryContent = "요약 내용",
                        ),
                    ),
            )

        Mockito
            .`when`(dayArchiveResolver.resolveTodayContentArchive(1L, publishedDate))
            .thenReturn(archive)
        Mockito
            .`when`(popularNewsletterSnapshotService.findLatestFeaturedExposureContent())
            .thenReturn(null)

        mockMvc
            .perform(
                get("/api/newsletters/contents/1")
                    .param("publishedDate", publishedDate.toString()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.publishedDate").value("2025-07-08"))
            .andExpect(jsonPath("$.trendingCard.id").value(11L))
            .andExpect(jsonPath("$.trendingCard.title").value("후킹 제목"))
            .andExpect(jsonPath("$.trendingCard.topKeyword").value("Kotlin"))
            .andExpect(jsonPath("$.cards[0].id").value(11L))
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
        Mockito
            .`when`(tokenUtil.validateAndGetEmail(validToken))
            .thenReturn("admin@example.com")
        Mockito
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
        Mockito
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
        Mockito
            .`when`(tokenUtil.validateAndGetEmail(validToken))
            .thenReturn("admin@example.com")
        Mockito
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
