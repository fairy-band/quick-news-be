package com.nexters.api.util

import com.nexters.external.repository.AdminMemberRepository
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class TokenUtil(
    private val adminMemberRepository: AdminMemberRepository,
) {
    fun validateAndGetEmail(token: String): String =
        try {
            val decoded = String(Base64.getDecoder().decode(token))
            val email =
                decoded.split(":").firstOrNull()
                    ?: throw IllegalArgumentException("Invalid token format")

            val adminMember =
                adminMemberRepository.findActiveByEmail(email)
                    ?: throw IllegalArgumentException("Admin member not found or inactive")

            adminMember.email
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid token: ${e.message}")
        }
}
