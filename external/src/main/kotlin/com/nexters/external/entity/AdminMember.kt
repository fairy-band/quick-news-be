package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "admin_members")
class AdminMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Column(unique = true, nullable = false)
    val email: String,
    @Column(nullable = false)
    val name: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "last_login_at")
    private var _lastLoginAt: LocalDateTime? = null,
    @Column(nullable = false)
    private var _active: Boolean = true
) {
    val lastLoginAt: LocalDateTime? get() = _lastLoginAt
    val isActive: Boolean get() = _active

    fun updateLastLoginAt() {
        _lastLoginAt = LocalDateTime.now()
    }

    fun deactivate() {
        _active = false
    }

    fun activate() {
        _active = true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdminMember
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "AdminMember(id=$id, email='$email', name='$name', active=$_active)"

    companion object {
        fun create(
            email: String,
            name: String
        ): AdminMember {
            require(email.isNotBlank()) { "이메일은 필수입니다" }
            require(name.isNotBlank()) { "이름은 필수입니다" }

            return AdminMember(
                email = email.trim(),
                name = name.trim()
            )
        }
    }
}
