package com.nexters.admin.domain.admin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("AdminMember 도메인 테스트")
class AdminMemberTest {
    @Test
    @DisplayName("AdminMember 생성 테스트")
    fun `AdminMember를 생성할 수 있다`() {
        // given
        val email = "admin@example.com"
        val name = "관리자"

        // when
        val adminMember = AdminMember.create(email, name)

        // then
        assertThat(adminMember.email).isEqualTo(email)
        assertThat(adminMember.name).isEqualTo(name)
        assertThat(adminMember.isActive).isTrue()
        assertThat(adminMember.createdAt).isBeforeOrEqualTo(LocalDateTime.now())
        assertThat(adminMember.lastLoginAt).isNull()
    }

    @Test
    @DisplayName("로그인 시간 업데이트 테스트")
    fun `로그인 시간을 업데이트할 수 있다`() {
        // given
        val adminMember = AdminMember.create("admin@example.com", "관리자")
        val beforeUpdate = LocalDateTime.now()

        // when
        adminMember.updateLastLoginAt()

        // then
        assertThat(adminMember.lastLoginAt).isNotNull()
        assertThat(adminMember.lastLoginAt).isAfterOrEqualTo(beforeUpdate)
    }

    @Test
    @DisplayName("계정 비활성화 테스트")
    fun `계정을 비활성화할 수 있다`() {
        // given
        val adminMember = AdminMember.create("admin@example.com", "관리자")

        // when
        adminMember.deactivate()

        // then
        assertThat(adminMember.isActive).isFalse()
    }

    @Test
    @DisplayName("계정 활성화 테스트")
    fun `비활성화된 계정을 다시 활성화할 수 있다`() {
        // given
        val adminMember = AdminMember.create("admin@example.com", "관리자")
        adminMember.deactivate()

        // when
        adminMember.activate()

        // then
        assertThat(adminMember.isActive).isTrue()
    }
}
