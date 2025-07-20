package com.nexters.api.controller

import com.nexters.api.dto.ContentViewApiResponse
import com.nexters.api.dto.ContentViewApiResponse.ContentCardApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/newsletters")
@Tag(name = "Newsletter API", description = "뉴스레터 관련 API")
class NewsletterApiController {
    @GetMapping("/contents/{userId}")
    fun getNewsletterContents(
        @PathVariable userId: Long,
        publishedDate: LocalDate = LocalDate.now()
    ): ContentViewApiResponse =
        ContentViewApiResponse(
            publishedDate = publishedDate,
            cards =
                listOf(
                    ContentCardApiResponse(
                        title = "OOP는 끝났다? Java 교육 방식에 대격변 예고!",
                        category = "BE",
                        topKeyword = "Java 교육",
                        summary = "객체지향은 이제 옛말? 함수형 사고와 데이터 중심 설계로 전환 중인 Java 교육, 그 충격의 현장.",
                        contentUrl = "https://max.xz.ax/blog/rethinking-oop/",
                        newsletterName = "The Awesome Java Weekly",
                        newsletterUrl = "https://java.libhunt.com/newsletter/478"
                    )
                )
        )
}
