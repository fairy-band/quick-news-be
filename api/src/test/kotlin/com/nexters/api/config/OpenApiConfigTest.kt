package com.nexters.api.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiConfigTest {
    private val sut = OpenApiConfig()

    @Test
    fun `OpenAPI server should use current origin`() {
        val openAPI = sut.openAPI()

        assertThat(openAPI.servers).hasSize(1)
        assertThat(openAPI.servers[0].url).isEqualTo("/")
        assertThat(openAPI.servers[0].description).isEqualTo("현재 접속한 호스트")
    }
}
