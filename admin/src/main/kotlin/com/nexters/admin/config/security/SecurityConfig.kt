package com.nexters.admin.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val fairyBandAdminOidcUserService: FairyBandAdminOidcUserService,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests(::configureAuthorization)
            .oauth2Login(::configureOAuth2Login)
            .logout(::configureLogout)
            .build()

    private fun configureAuthorization(auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry) {
        auth
            .requestMatchers(*PUBLIC_ENDPOINTS)
            .permitAll()
            .requestMatchers(*STATIC_RESOURCES)
            .permitAll()
            .anyRequest()
            .authenticated()
    }

    private fun configureOAuth2Login(oauth2: OAuth2LoginConfigurer<HttpSecurity>,) {
        oauth2
            .loginPage(LOGIN_PAGE)
            .userInfoEndpoint { userInfo ->
                userInfo.oidcUserService(fairyBandAdminOidcUserService)
            }.successHandler(createSuccessHandler())
            .failureHandler(createFailureHandler())
    }

    private fun configureLogout(logout: LogoutConfigurer<HttpSecurity>) {
        logout
            .logoutUrl(LOGOUT_URL)
            .logoutSuccessUrl(LOGOUT_SUCCESS_URL)
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies(*COOKIES_TO_DELETE)
    }

    @Bean
    fun authenticationSuccessHandler(): AuthenticationSuccessHandler = createSuccessHandler()

    @Bean
    fun authenticationFailureHandler(): AuthenticationFailureHandler = createFailureHandler()

    private fun createSuccessHandler(): AuthenticationSuccessHandler =
        AuthenticationSuccessHandler { _, response, _ ->
            response.sendRedirect(HOME_PAGE)
        }

    private fun createFailureHandler(): AuthenticationFailureHandler =
        AuthenticationFailureHandler { _, response, exception ->
            val errorMessage = exception.message ?: DEFAULT_ERROR_MESSAGE
            val encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8)
            response.sendRedirect("$LOGIN_PAGE?error=$encodedMessage")
        }

    companion object {
        private val PUBLIC_ENDPOINTS = arrayOf("/health", "/error", "/login/**", "/oauth2/**")
        private val STATIC_RESOURCES = arrayOf("/css/**", "/js/**", "/images/**", "/favicon.ico")
        private val COOKIES_TO_DELETE = arrayOf("JSESSIONID")

        private const val LOGIN_PAGE = "/login"
        private const val HOME_PAGE = "/admin/"
        private const val LOGOUT_URL = "/logout"
        private const val LOGOUT_SUCCESS_URL = "/login?logout"
        private const val DEFAULT_ERROR_MESSAGE = "로그인 실패"
    }
}
