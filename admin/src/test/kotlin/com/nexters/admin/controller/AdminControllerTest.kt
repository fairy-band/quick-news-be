package com.nexters.admin.controller

import com.nexters.admin.domain.admin.AdminMemberRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.TestConstructor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(AdminController::class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("AdminController 테스트")
class AdminControllerTest(
    private val mockMvc: MockMvc
) {
    
    @MockBean
    private lateinit var adminMemberRepository: AdminMemberRepository
    
    @Test
    @DisplayName("로그인 페이지 접근 테스트")
    fun `로그인 페이지에 접근할 수 있다`() {
        // when & then
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk)
            .andExpect(view().name("login"))
    }
    
    @Test
    @DisplayName("로그인 실패시 에러 메시지 표시 테스트")
    fun `로그인 실패시 에러 메시지가 표시된다`() {
        // given
        val errorMessage = "접근 권한이 없습니다"
        
        // when & then
        mockMvc.perform(get("/login").param("error", errorMessage))
            .andExpect(status().isOk)
            .andExpect(view().name("login"))
            .andExpect(model().attribute("errorMessage", errorMessage))
    }
    
    @Test
    @DisplayName("로그아웃 성공시 메시지 표시 테스트")
    fun `로그아웃 성공시 메시지가 표시된다`() {
        // when & then
        mockMvc.perform(get("/login").param("logout", ""))
            .andExpect(status().isOk)
            .andExpect(view().name("login"))
            .andExpect(model().attribute("logoutMessage", "성공적으로 로그아웃되었습니다."))
    }
    
    @Test
    @DisplayName("인증 없이 관리자 홈 접근시 리다이렉트 테스트")
    fun `인증 없이 관리자 홈에 접근하면 로그인 페이지로 리다이렉트된다`() {
        // when & then
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection)
    }
    
    @Test
    @WithMockUser
    @DisplayName("인증된 사용자의 관리자 홈 접근 테스트")
    fun `인증된 사용자는 관리자 홈에 접근할 수 있다`() {
        // when & then
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/home"))
    }
    
    @Test
    @DisplayName("/admin 경로 리다이렉트 테스트")
    fun `/admin 경로는 루트로 리다이렉트된다`() {
        // when & then
        mockMvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
    }
}
