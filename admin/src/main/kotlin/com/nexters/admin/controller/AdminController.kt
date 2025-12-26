package com.nexters.admin.controller

import mu.KotlinLogging
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
    fun index(): String = "redirect:/dashboard"

    @GetMapping("/keywords")
    fun keywords(): String = "keywords"

    @GetMapping("/contents")
    fun contents(): String = "contents"

    @GetMapping("/recommendations")
    fun recommendations(): String = REDIRECT_TO_HOME

    @GetMapping("/exposure-fix")
    fun exposureFix(): String = "exposure-fix"

    @GetMapping("/rss-reader")
    fun rssReader(): String = "rss-reader"

    @GetMapping("/users")
    fun users(): String = "users"

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

    companion object {
        private const val LOGIN_VIEW = "login"
        private const val REDIRECT_TO_HOME = "redirect:/"

        private const val ERROR_MESSAGE_KEY = "errorMessage"
        private const val LOGOUT_MESSAGE_KEY = "logoutMessage"

        private const val LOGOUT_SUCCESS_MESSAGE = "성공적으로 로그아웃되었습니다."
    }
}
