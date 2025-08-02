package com.nexters.external.repository

import com.nexters.external.entity.AdminMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AdminMemberRepository : JpaRepository<AdminMember, Long> {
    @Query("SELECT am FROM AdminMember am WHERE am.email = :email AND am._active = true")
    fun findActiveByEmail(
        @Param("email") email: String
    ): AdminMember?

    @Query("SELECT am FROM AdminMember am WHERE am.email = :email")
    fun findByEmailIncludingInactive(
        @Param("email") email: String
    ): AdminMember?

    @Query("SELECT CASE WHEN COUNT(am) > 0 THEN true ELSE false END FROM AdminMember am WHERE am.email = :email AND am._active = true")
    fun existsActiveByEmail(
        @Param("email") email: String
    ): Boolean

    // 더 명확한 메서드명으로 alias 추가
    fun findByEmail(email: String): AdminMember? = findActiveByEmail(email)

    fun existsByEmailAndActiveTrue(email: String): Boolean = existsActiveByEmail(email)
}
