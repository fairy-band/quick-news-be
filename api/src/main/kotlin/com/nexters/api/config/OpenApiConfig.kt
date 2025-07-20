package com.nexters.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
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
}
