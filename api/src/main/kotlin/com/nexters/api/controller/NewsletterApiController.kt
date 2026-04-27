package com.nexters.api.controller

import com.nexters.api.dto.ContentProviderApiResponse
import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.dto.CreateContentApiRequest
import com.nexters.api.dto.CreateContentApiResponse
import com.nexters.api.dto.CreateContentProviderRequestApiRequest
import com.nexters.api.dto.ExposureContentApiResponse
import com.nexters.api.dto.ExposureContentListApiResponse
import com.nexters.api.enums.ExploreSortType
import com.nexters.api.enums.Language
import com.nexters.api.exception.UnauthorizedException
import com.nexters.api.service.ExploreContentResult
import com.nexters.api.service.ExploreContentsResult
import com.nexters.api.service.NewsletterContentsService
import com.nexters.api.service.NewsletterExploreService
import com.nexters.api.util.TokenUtil
import com.nexters.external.service.ContentProviderRequestService
import com.nexters.external.service.ContentService
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/newsletters")
@Tag(name = "Newsletter API", description = "뉴스레터 관련 API")
class NewsletterApiController(
    private val dayArchiveResolver: DailyContentArchiveResolver,
    private val newsletterContentsService: NewsletterContentsService,
    private val newsletterExploreService: NewsletterExploreService,
    private val contentService: ContentService,
    private val contentProviderRequestService: ContentProviderRequestService,
    private val tokenUtil: TokenUtil,
) {
    @GetMapping("/contents/{userId}")
    fun getNewsletterContents(
        @PathVariable userId: Long,
        publishedDate: LocalDate = LocalDate.now(),
    ): ContentViewApiResponse = newsletterContentsService.getNewsletterContents(userId, publishedDate)

    @PostMapping("/contents/{userId}/refresh")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "콘텐츠 새로고침 성공"),
            ApiResponse(responseCode = "400", description = "콘텐츠 새로고침 실패, 회수 초과"),
        ],
    )
    fun refreshContents(
        @PathVariable userId: Long,
    ) {
        dayArchiveResolver.refreshTodayArchives(userId)
    }

    @GetMapping("/explore/contents")
    @Operation(
        summary = "탐색 콘텐츠 조회",
        description = "노출 콘텐츠 목록을 keyset cursor pagination으로 조회합니다. " +
            "sort 없음(기본): 등록 ID 내림차순. sort=published: 발행일(published_at) 내림차순, 동일 발행일은 ID 내림차순. " +
            "다음 페이지 조회 시 응답의 nextOffset을 lastSeenOffset에 전달합니다.",
    )
    fun getExploreContents(
        @Parameter(description = "이전 페이지 응답의 nextOffset 값. 첫 페이지는 0(기본값).", example = "0")
        @RequestParam(defaultValue = "0") lastSeenOffset: Long,
        @Parameter(description = "한 번에 가져올 콘텐츠 개수. 1 이상 값만 허용합니다. (기본값: 20)", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "정렬 기준 (published: 발행일 최신순)", example = "published")
        @RequestParam(required = false) sort: String?,
    ): ExposureContentListApiResponse =
        newsletterExploreService
            .getExploreContents(lastSeenOffset, size, ExploreSortType.from(sort))
            .toApiResponse()

    @PostMapping("/contents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "콘텐츠 등록",
        description = "새로운 콘텐츠를 등록합니다. contentProviderName이 없으면 자동으로 생성됩니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "콘텐츠 등록 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
        ],
    )
    fun createContent(
        @RequestHeader("Access-Token") accessToken: String,
        @RequestBody request: CreateContentApiRequest,
    ): CreateContentApiResponse {
        try {
            tokenUtil.validateAndGetEmail(accessToken)
        } catch (e: Exception) {
            throw UnauthorizedException("Invalid access token: ${e.message}")
        }

        val content =
            contentService.createContent(
                title = request.title,
                content = request.content,
                originalUrl = request.originalUrl,
                publishedAt = request.publishedAt,
                contentProviderName = request.contentProviderName,
                imageUrl = request.imageUrl,
                contentProviderType = request.contentProviderType,
            )

        return CreateContentApiResponse(
            id = content.id!!,
            title = content.title,
            newsletterName = content.newsletterName,
            originalUrl = content.originalUrl,
            createdAt = content.createdAt,
        )
    }

    @GetMapping("/content-providers")
    @Operation(
        summary = "콘텐츠 제공자 목록 조회",
        description = "등록된 모든 콘텐츠 제공자 목록을 조회합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공"),
            ApiResponse(responseCode = "401", description = "인증 실패"),
        ],
    )
    fun getContentProviders(
        @RequestHeader("Access-Token") accessToken: String,
    ): List<ContentProviderApiResponse> {
        try {
            tokenUtil.validateAndGetEmail(accessToken)
        } catch (e: Exception) {
            throw UnauthorizedException("Invalid access token: ${e.message}")
        }

        return contentService.getAllContentProviders().map { provider ->
            ContentProviderApiResponse(
                id = provider.id!!,
                name = provider.name,
                channel = provider.channel,
                language = provider.language,
                type = provider.type,
            )
        }
    }

    @PostMapping("/content-provider-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "콘텐츠 제공자 요청",
        description = "새로운 콘텐츠 제공자 추가를 요청합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "요청 등록 성공"),
            ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ],
    )
    fun createContentProviderRequest(
        @RequestBody request: CreateContentProviderRequestApiRequest,
    ) {
        contentProviderRequestService.createRequest(
            contentProviderName = request.contentProviderName,
            channel = request.channel,
            requestCategory = request.requestCategories.joinToString { "," },
            relatedTo = request.relatedTo,
            reason = request.reason,
        )
    }
}

private fun ExploreContentsResult.toApiResponse(): ExposureContentListApiResponse =
    ExposureContentListApiResponse(
        contents = contents.map { it.toApiResponse() },
        totalCount = totalCount,
        hasMore = hasMore,
        nextOffset = nextOffset,
    )

private fun ExploreContentResult.toApiResponse(): ExposureContentApiResponse =
    ExposureContentApiResponse(
        id = id,
        contentId = contentId,
        provocativeKeyword = provocativeKeyword,
        provocativeHeadline = provocativeHeadline,
        summaryContent = summaryContent,
        contentUrl = contentUrl,
        imageUrl = imageUrl,
        newsletterName = newsletterName,
        language = Language.fromString(language),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
