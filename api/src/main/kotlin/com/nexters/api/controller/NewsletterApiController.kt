package com.nexters.api.controller

import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.dto.ContentViewApiResponse.ContentCardApiResponse
import com.nexters.api.dto.ExposureContentApiResponse
import com.nexters.api.dto.ExposureContentListApiResponse
import com.nexters.api.enums.Language
import com.nexters.external.service.ExposureContentService
import com.nexters.newsletter.resolver.DailyContentArchiveResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/newsletters")
@Tag(name = "Newsletter API", description = "뉴스레터 관련 API")
class NewsletterApiController(
    private val dayArchiveResolver: DailyContentArchiveResolver,
    private val exposureContentService: ExposureContentService,
) {
    @GetMapping("/contents/{userId}")
    fun getNewsletterContents(
        @PathVariable userId: Long,
        publishedDate: LocalDate = LocalDate.now(),
    ): ContentViewApiResponse =
        ContentViewApiResponse(
            publishedDate = publishedDate,
            cards =
                dayArchiveResolver.resolveTodayContentArchive(userId, publishedDate).exposureContents.map {
                    ContentCardApiResponse(
                        id = it.id!!,
                        title = it.provocativeHeadline,
                        topKeyword = it.provocativeKeyword,
                        summary = it.summaryContent,
                        contentUrl = it.content.originalUrl,
                        newsletterName = it.content.newsletterName,
                        language = Language.fromString(it.content.contentProvider?.language),
                    )
                },
        )

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
        description = "노출 콘텐츠 목록을 무한 페이징으로 조회합니다. lastSeenOffset을 사용하여 다음 페이지를 요청할 수 있습니다.",
    )
    fun getExploreContents(
        @Parameter(description = "마지막으로 본 콘텐츠의 ID (기본값: 0)", example = "0")
        @RequestParam(defaultValue = "0") lastSeenOffset: Long,
        @Parameter(description = "한 번에 가져올 콘텐츠 개수 (기본값: 20)", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ExposureContentListApiResponse {
        val pageable = PageRequest.of(0, size)
        val page = exposureContentService.getAllExposureContentsWithPaging(lastSeenOffset, pageable)

        val contents =
            page.content.map { exposureContent ->
                ExposureContentApiResponse(
                    id = exposureContent.id!!,
                    contentId = exposureContent.content.id!!,
                    provocativeKeyword = exposureContent.provocativeKeyword,
                    provocativeHeadline = exposureContent.provocativeHeadline,
                    summaryContent = exposureContent.summaryContent,
                    contentUrl = exposureContent.content.originalUrl,
                    newsletterName = exposureContent.content.newsletterName,
                    language = Language.fromString(exposureContent.content.contentProvider?.language),
                    createdAt = exposureContent.createdAt,
                    updatedAt = exposureContent.updatedAt,
                )
            }

        val hasMore = page.content.size == size
        val nextOffset = if (hasMore && contents.isNotEmpty()) contents.last().id else null

        return ExposureContentListApiResponse(
            contents = contents,
            hasMore = hasMore,
            nextOffset = nextOffset,
        )
    }
}
