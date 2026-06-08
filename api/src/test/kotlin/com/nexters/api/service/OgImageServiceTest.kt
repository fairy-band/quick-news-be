package com.nexters.api.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OgImageServiceTest {
    private val sut = OgImageService()

    @Test
    fun `generate OG image should accept color with hash prefix`() {
        val imageBytes =
            sut.generateOgImage(
                title = "테스트 제목",
                tag = "신기술",
                newsletterName = "테스트 뉴스레터",
                textColor = "#DCFF64",
            )

        assertThat(imageBytes.copyOfRange(0, 4))
            .containsExactly(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    }

    @Test
    fun `generate OG image should accept color without hash prefix`() {
        val imageBytes =
            sut.generateOgImage(
                title = "테스트 제목",
                tag = "신기술",
                newsletterName = "테스트 뉴스레터",
                textColor = "DCFF64",
            )

        assertThat(imageBytes.copyOfRange(0, 4))
            .containsExactly(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    }

    @Test
    fun `generate OG image should fallback to default color when color is invalid`() {
        val imageBytes =
            sut.generateOgImage(
                title = "테스트 제목",
                tag = "신기술",
                newsletterName = "테스트 뉴스레터",
                textColor = "invalid",
            )

        assertThat(imageBytes.copyOfRange(0, 4))
            .containsExactly(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    }
}
