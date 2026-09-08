package com.nexters.external.service

import com.nexters.external.entity.UserReadContent
import com.nexters.external.repository.UserReadContentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserReadContentServiceTest {

    private val userReadContentRepository: UserReadContentRepository = mockk()
    private lateinit var service: UserReadContentService

    @BeforeEach
    fun setUp() {
        service = UserReadContentService(userReadContentRepository)
    }

    @Test
    fun `신규 아티클 읽음 시 readCount=1 로 저장되어야 한다`() {
        val userId = 1L
        val exposureContentId = 100L
        val contentId = 500L

        every { userReadContentRepository.findByUserIdAndExposureContentId(userId, exposureContentId) } returns null
        val saveSlot = slot<UserReadContent>()
        every { userReadContentRepository.save(capture(saveSlot)) } answers { saveSlot.captured }

        val result = service.recordRead(userId, exposureContentId, contentId)

        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.exposureContentId).isEqualTo(exposureContentId)
        assertThat(result.contentId).isEqualTo(contentId)
        assertThat(result.readCount).isEqualTo(1)
        verify(exactly = 1) { userReadContentRepository.save(any()) }
    }

    @Test
    fun `이미 읽은 아티클을 다시 열람하면 readCount 가 증가해야 한다`() {
        val userId = 1L
        val exposureContentId = 100L
        val contentId = 500L
        val existing = UserReadContent(
            id = 10L,
            userId = userId,
            exposureContentId = exposureContentId,
            contentId = contentId,
            readCount = 1,
        )

        every { userReadContentRepository.findByUserIdAndExposureContentId(userId, exposureContentId) } returns existing
        every { userReadContentRepository.save(existing) } returns existing

        val result = service.recordRead(userId, exposureContentId, contentId)

        assertThat(result.readCount).isEqualTo(2)
        verify(exactly = 1) { userReadContentRepository.save(existing) }
    }

    @Test
    fun `getReadExposureContentIds 는 유저가 읽은 카드 ID 집합을 반환해야 한다`() {
        val userId = 1L
        val queryIds = listOf(100L, 101L, 102L)
        val readRecords = listOf(
            UserReadContent(id = 1L, userId = userId, exposureContentId = 100L, contentId = 500L),
            UserReadContent(id = 2L, userId = userId, exposureContentId = 102L, contentId = 502L),
        )

        every { userReadContentRepository.findAllByUserIdAndExposureContentIdIn(userId, queryIds) } returns readRecords

        val result = service.getReadExposureContentIds(userId, queryIds)

        assertThat(result).containsExactlyInAnyOrder(100L, 102L)
    }
}
