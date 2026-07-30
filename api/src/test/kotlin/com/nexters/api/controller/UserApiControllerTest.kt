package com.nexters.api.controller

import com.nexters.external.entity.Category
import com.nexters.external.entity.User
import com.nexters.external.service.UserService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(UserApiController::class)
class UserApiControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserService

    @Test
    fun `getUserInfo should include isCategoryChanged and categoryChangeCount in response`() {
        val userId = 1L
        val user =
            User(
                id = userId,
                deviceToken = "test-token",
                categories = mutableSetOf(Category(id = 1L, name = "BE")),
                isOnboarded = true,
                isCategoryChanged = true,
                categoryChangeCount = 1,
            )

        given(userService.getUserById(userId)).willReturn(user)

        mockMvc
            .perform(get("/api/users/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.isOnboarded").value(true))
            .andExpect(jsonPath("$.isCategoryChanged").value(true))
            .andExpect(jsonPath("$.categoryChangeCount").value(1))
    }
}
