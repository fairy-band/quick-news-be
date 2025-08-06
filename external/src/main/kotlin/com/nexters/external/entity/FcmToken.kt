package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.Objects

@Entity
@Table(
    name = "fcm_tokens",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "device_token"]),
    ],
    indexes = [
        Index(name = "idx_user_id_active", columnList = "user_id, is_active"),
        Index(name = "idx_device_token", columnList = "device_token"),
    ],
)
class FcmToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "device_token", nullable = false, length = 500)
    val deviceToken: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    val deviceType: DeviceType,
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FcmToken) return false
        return userId == other.userId && deviceToken == other.deviceToken
    }

    override fun hashCode(): Int = Objects.hash(userId, deviceToken)
}

enum class DeviceType {
    ANDROID,
    IOS
}
