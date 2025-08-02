package com.nexters.admin.controller

import mu.KotlinLogging
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AdminController {
    private val logger = KotlinLogging.logger {}

    @GetMapping("/login")
    fun loginPage(
        @RequestParam("error", required = false) error: String?,
        @RequestParam("logout", required = false) logout: String?,
        model: Model
    ): String {
        handleLoginError(error, model)
        handleLogoutMessage(logout, model)

        return LOGIN_VIEW
    }

    @GetMapping("/")
    fun index(): String = "contents"

    @GetMapping("/metrics")
    fun metrics(): String = "metrics"

    @GetMapping("/keywords")
    fun keywords(): String = "keywords"

    @GetMapping("/contents")
    fun contents(): String = "contents"

    @GetMapping("/recommendations")
    fun recommendations(): String = "recommendations"

    @GetMapping("/admin")
    fun adminRedirect(): String = REDIRECT_TO_HOME

    private fun handleLoginError(
        error: String?,
        model: Model
    ) {
        error?.let {
            model.addAttribute(ERROR_MESSAGE_KEY, it)
            logger.warn { "로그인 실패: $it" }
        }
    }

    private fun handleLogoutMessage(
        logout: String?,
        model: Model
    ) {
        logout?.let {
            model.addAttribute(LOGOUT_MESSAGE_KEY, LOGOUT_SUCCESS_MESSAGE)
            logger.info { "로그아웃 완료" }
        }
    }

    private fun extractUserInfo(oidcUser: OidcUser): UserInfo =
        UserInfo(
            email = oidcUser.getAttribute<String>("email").orEmpty(),
            name = oidcUser.getAttribute<String>("name").orEmpty()
        )

    private fun addUserInfoToModel(
        userInfo: UserInfo,
        model: Model
    ) {
        model.addAttribute(USER_EMAIL_KEY, userInfo.email)
        model.addAttribute(USER_NAME_KEY, userInfo.name)
    }

    private fun logHomeAccess(userInfo: UserInfo) {
        logger.info { "관리자 홈 접근. email: ${userInfo.email}, name: ${userInfo.name}" }
    }

    private data class UserInfo(
        val email: String,
        val name: String
    )

    companion object {
        private const val LOGIN_VIEW = "login"
        private const val ADMIN_HOME_VIEW = "admin/home"
        private const val REDIRECT_TO_HOME = "redirect:/"

        private const val ERROR_MESSAGE_KEY = "errorMessage"
        private const val LOGOUT_MESSAGE_KEY = "logoutMessage"
        private const val USER_EMAIL_KEY = "userEmail"
        private const val USER_NAME_KEY = "userName"

        private const val LOGOUT_SUCCESS_MESSAGE = "성공적으로 로그아웃되었습니다."
    }
}
