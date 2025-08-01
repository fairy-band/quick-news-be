package com.nexters.admin.config.security

import com.nexters.admin.domain.admin.AdminMemberRepository
import mu.KotlinLogging
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FairyBandAdminOidcUserService(
    private val adminMemberRepository: AdminMemberRepository
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val logger = KotlinLogging.logger {}
    private val oidcUserService = OidcUserService()

    @Transactional
    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val loadedUser = oidcUserService.loadUser(userRequest)

        validateAdminAccess(loadedUser)
        updateLastLoginTime(loadedUser)

        val userInfo = extractUserInfo(loadedUser)
        logger.info { "✅ 관리자 로그인 성공. email: ${userInfo.email}, name: ${userInfo.name}" }

        return loadedUser
    }

    private fun validateAdminAccess(oidcUser: OidcUser) {
        val email = extractEmail(oidcUser)

        if (email.isBlank()) {
            logger.warn { "⚠️ 이메일 정보가 없습니다." }
            throwAccessDeniedException("이메일 정보가 없습니다")
        }

        if (!adminMemberRepository.existsByEmailAndActiveTrue(email)) {
            logger.warn { "❌ 관리자 접근 권한이 없습니다. email: $email" }
            throwAccessDeniedException("접근 권한이 없습니다. email: $email")
        }

        logger.debug { "🔍 관리자 권한 확인 완료. email: $email" }
    }

    private fun updateLastLoginTime(oidcUser: OidcUser) {
        val email = extractEmail(oidcUser)
        if (email.isNotBlank()) {
            adminMemberRepository.findByEmail(email)?.run {
                updateLastLoginAt()
                adminMemberRepository.save(this)
            }
        }
    }

    private fun extractUserInfo(oidcUser: OidcUser): UserInfo {
        return UserInfo(
            email = extractEmail(oidcUser),
            name = extractName(oidcUser)
        )
    }

    private fun extractEmail(oidcUser: OidcUser): String {
        return oidcUser.getAttribute<String>("email")?.trim().orEmpty()
    }

    private fun extractName(oidcUser: OidcUser): String {
        return oidcUser.getAttribute<String>("name")?.trim() ?: UNKNOWN_USER_NAME
    }

    private fun throwAccessDeniedException(message: String): Nothing {
        throw OAuth2AuthenticationException(
            OAuth2Error("access_denied", message, null)
        )
    }

    private data class UserInfo(
        val email: String,
        val name: String
    )

    companion object {
        private const val UNKNOWN_USER_NAME = "Unknown"
    }
}
