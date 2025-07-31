package com.nexters.external.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "user_exposed_contents_mapping")
@IdClass(UserExposedContentMapping.UserExposedContentMappingId::class)
class UserExposedContentMapping(
    @Id
    @Column(name = "user_id")
    val userId: Long,
    @Id
    @Column(name = "content_id")
    val contentId: Long,
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "content_id", insertable = false, updatable = false)
    val content: Content? = null

    data class UserExposedContentMappingId(
        val userId: Long,
        val contentId: Long
    ) : Serializable
}
