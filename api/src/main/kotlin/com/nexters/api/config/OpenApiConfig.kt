package com.nexters.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.ForwardedHeaderFilter

@Configuration
class OpenApiConfig(
    @Value("\${server.url:http://localhost:8080}") private val swaggerBaseUrl: String
) {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Newsletter Feeder API")
                    .description("뉴스레터 피더 서비스 API 문서")
                    .version("v1.0.0")
                    .contact(
                        Contact()
                            .name("Nexters")
                            .url("https://github.com/Nexters/newsletter-feeder")
                    )
            )
            .addServersItem(Server().url("http://121.78.130.82/api").description("가비아"))
            .addServersItem(Server().url(swaggerBaseUrl).description("로컬 개발 서버"))

    @Bean
    fun forwardedHeaderFilter(): ForwardedHeaderFilter = ForwardedHeaderFilter()
}
