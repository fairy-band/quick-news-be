package com.nexters.external.repository

import com.nexters.external.entity.AdminMember
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
@Transactional
@DisplayName("AdminMemberRepository 테스트")
class AdminMemberRepositoryTest {
    @Autowired
    lateinit var adminMemberRepository: AdminMemberRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    @Test
    @DisplayName("이메일로 활성 관리자 조회 테스트")
    fun `활성화된 관리자를 이메일로 조회할 수 있다`() {
        // given
        val email = "admin@example.com"
        val adminMember = AdminMember.Companion.create(email, "관리자")
        entityManager.persistAndFlush(adminMember)

        // when
        val foundMember = adminMemberRepository.findByEmail(email)

        // then
        Assertions.assertThat(foundMember).isNotNull
        Assertions.assertThat(foundMember?.email).isEqualTo(email)
        Assertions.assertThat(foundMember?.isActive).isTrue()
    }

    @Test
    @DisplayName("비활성화된 관리자는 findByEmail로 조회되지 않는다")
    fun `비활성화된 관리자는 조회되지 않는다`() {
        // given
        val email = "admin@example.com"
        val adminMember = AdminMember.Companion.create(email, "관리자")
        adminMember.deactivate()
        entityManager.persistAndFlush(adminMember)

        // when
        val foundMember = adminMemberRepository.findByEmail(email)

        // then
        Assertions.assertThat(foundMember).isNull()
    }

    @Test
    @DisplayName("비활성화된 관리자도 findByEmailIncludingInactive로 조회 가능하다")
    fun `비활성화된 관리자도 별도 메서드로 조회할 수 있다`() {
        // given
        val email = "admin@example.com"
        val adminMember = AdminMember.Companion.create(email, "관리자")
        adminMember.deactivate()
        entityManager.persistAndFlush(adminMember)

        // when
        val foundMember = adminMemberRepository.findByEmailIncludingInactive(email)

        // then
        Assertions.assertThat(foundMember).isNotNull
        Assertions.assertThat(foundMember?.email).isEqualTo(email)
        Assertions.assertThat(foundMember?.isActive).isFalse()
    }

    @Test
    @DisplayName("활성 관리자 존재 여부 확인 테스트")
    fun `활성화된 관리자의 존재 여부를 확인할 수 있다`() {
        // given
        val email = "admin@example.com"
        val adminMember = AdminMember.Companion.create(email, "관리자")
        entityManager.persistAndFlush(adminMember)

        // when
        val exists = adminMemberRepository.existsByEmailAndActiveTrue(email)

        // then
        Assertions.assertThat(exists).isTrue()
    }

    @Test
    @DisplayName("비활성화된 관리자는 존재하지 않는 것으로 확인된다")
    fun `비활성화된 관리자는 존재하지 않는 것으로 확인된다`() {
        // given
        val email = "admin@example.com"
        val adminMember = AdminMember.Companion.create(email, "관리자")
        adminMember.deactivate()
        entityManager.persistAndFlush(adminMember)

        // when
        val exists = adminMemberRepository.existsByEmailAndActiveTrue(email)

        // then
        Assertions.assertThat(exists).isFalse()
    }
}
